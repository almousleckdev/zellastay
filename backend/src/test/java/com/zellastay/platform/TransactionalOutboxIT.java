package com.zellastay.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zellastay.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import outboxfixtures.OutboxProbeConfiguration;
import outboxfixtures.OutboxProbeEvent;
import outboxfixtures.RecordingOutboxListener;

@IntegrationTest
@Import(OutboxProbeConfiguration.class)
class TransactionalOutboxIT {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private RecordingOutboxListener listener;
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
          events.publishEvent(new OutboxProbeEvent(id));
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
          events.publishEvent(new OutboxProbeEvent(id));
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
          events.publishEvent(new OutboxProbeEvent(id));
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
