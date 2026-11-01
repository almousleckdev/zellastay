package com.zellastay.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zellastay.support.IntegrationTest;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@Import(TransactionalOutboxIT.ProbeConfiguration.class)
class TransactionalOutboxIT {

  public record ProbeEvent(String id) {}

  public static class ProbeListener {
    private final Set<String> handled = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicBoolean failing = new AtomicBoolean(false);

    @ApplicationModuleListener
    public void on(ProbeEvent event) {
      if (failing.get()) {
        throw new IllegalStateException("simulated consumer failure");
      }
      handled.add(event.id());
    }

    public Set<String> handled() {
      return handled;
    }

    public void failing(boolean value) {
      failing.set(value);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {
    @Bean
    ProbeListener probeListener() {
      return new ProbeListener();
    }
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private ProbeListener listener;
  @Autowired private IncompleteEventPublications incompletePublications;

  private TransactionTemplate tx;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(transactionManager);
    jdbc.execute("CREATE TABLE IF NOT EXISTS outbox_probe (id TEXT PRIMARY KEY)");
    listener.handled().clear();
    listener.failing(false);
  }

  @Test
  void committedEventIsPublishedAndMarkedComplete() {
    String id = "commit-" + System.nanoTime();

    tx.executeWithoutResult(
        status -> {
          jdbc.update("INSERT INTO outbox_probe VALUES (?)", id);
          events.publishEvent(new ProbeEvent(id));
        });

    await().atMost(Duration.ofSeconds(10)).until(() -> listener.handled().contains(id));
    await().atMost(Duration.ofSeconds(10)).until(() -> completedPublications(id) == 1);
  }

  @Test
  void rolledBackTransactionPublishesNothing() {
    String id = "rollback-" + System.nanoTime();

    tx.executeWithoutResult(
        status -> {
          jdbc.update("INSERT INTO outbox_probe VALUES (?)", id);
          events.publishEvent(new ProbeEvent(id));
          status.setRollbackOnly();
        });

    assertThat(publications(id)).isZero();
    assertThat(listener.handled()).doesNotContain(id);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_probe WHERE id = ?", Integer.class, id))
        .isZero();
  }

  @Test
  void consumerFailureNeverRollsBackProducerAndEventStaysPendingUntilResubmitted() {
    String id = "failure-" + System.nanoTime();
    listener.failing(true);

    tx.executeWithoutResult(
        status -> {
          jdbc.update("INSERT INTO outbox_probe VALUES (?)", id);
          events.publishEvent(new ProbeEvent(id));
        });

    await().atMost(Duration.ofSeconds(10)).until(() -> publications(id) == 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_probe WHERE id = ?", Integer.class, id))
        .isEqualTo(1);
    assertThat(completedPublications(id)).isZero();
    assertThat(listener.handled()).doesNotContain(id);

    listener.failing(false);
    incompletePublications.resubmitIncompletePublications(publication -> true);

    await().atMost(Duration.ofSeconds(10)).until(() -> listener.handled().contains(id));
    await().atMost(Duration.ofSeconds(10)).until(() -> completedPublications(id) == 1);
  }

  private int publications(String id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?",
        Integer.class,
        "%" + id + "%");
  }

  private int completedPublications(String id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NOT NULL",
        Integer.class,
        "%" + id + "%");
  }
}
