package com.zellastay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@IntegrationTest
class AuditRecorderIT {

  @Autowired AuditRecorder recorder;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @AfterEach
  void clearRequest() {
    MDC.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  private static AuditEntry entry(UUID actorId, UUID subjectId, Map<String, Object> details) {
    return new AuditEntry(
        AuditActor.admin(actorId),
        TestAuditAction.LOGIN_SUCCESS,
        new AuditSubject("account", subjectId.toString()),
        AuditOutcome.SUCCESS,
        details);
  }

  private int countFor(UUID subjectId) {
    return jdbc.queryForObject(
        "select count(*) from audit_event where subject_id = ?",
        Integer.class,
        subjectId.toString());
  }

  @Test
  void recordPersistsTheEntryWithRequestMetadata() {
    MDC.put("requestId", "req-123");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.9");
    request.addHeader("User-Agent", "JUnit/5");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    UUID actor = UUID.randomUUID();
    UUID subject = UUID.randomUUID();

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> recorder.record(entry(actor, subject, Map.of("method", "password"))));

    Map<String, Object> row =
        jdbc.queryForMap("select * from audit_event where subject_id = ?", subject.toString());
    assertThat(row)
        .containsEntry("actor_type", "ADMIN")
        .containsEntry("action", "auth.login.success")
        .containsEntry("subject_type", "account")
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("ip", "203.0.113.9")
        .containsEntry("user_agent", "JUnit/5")
        .containsEntry("correlation_id", "req-123");
    assertThat(row.get("actor_id").toString()).isEqualTo(actor.toString());
    assertThat(row.get("details").toString()).contains("\"method\"").contains("password");
    assertThat(row.get("occurred_at")).isNotNull();
  }

  @Test
  void recordWorksOutsideARequest() {
    UUID subject = UUID.randomUUID();

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> recorder.record(entry(UUID.randomUUID(), subject, Map.of())));

    assertThat(
            jdbc.queryForObject(
                "select ip from audit_event where subject_id = ?",
                String.class,
                subject.toString()))
        .isNull();
  }

  @Test
  void recordRequiresACallerTransaction() {
    assertThatThrownBy(() -> recorder.record(entry(UUID.randomUUID(), UUID.randomUUID(), Map.of())))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void aPrivilegedEntryIsRolledBackWithTheCaller() {
    UUID subject = UUID.randomUUID();

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              recorder.record(entry(UUID.randomUUID(), subject, Map.of()));
              status.setRollbackOnly();
            });

    assertThat(countFor(subject)).isZero();
  }

  @Test
  void aBestEffortEntrySurvivesTheCallerRollingBack() {
    UUID subject = UUID.randomUUID();

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              recorder.recordBestEffort(entry(UUID.randomUUID(), subject, Map.of()));
              status.setRollbackOnly();
            });

    assertThat(countFor(subject)).isEqualTo(1);
  }

  @Test
  void bestEffortWorksWithoutACallerTransaction() {
    UUID subject = UUID.randomUUID();

    recorder.recordBestEffort(entry(UUID.randomUUID(), subject, Map.of()));

    assertThat(countFor(subject)).isEqualTo(1);
  }

  @Test
  void anInvalidEntryIsRejectedAndNothingIsStored() {
    UUID subject = UUID.randomUUID();
    Map<String, Object> bad = Map.of("undeclared", "x");

    assertThatThrownBy(() -> recorder.recordBestEffort(entry(UUID.randomUUID(), subject, bad)))
        .isInstanceOf(com.zellastay.audit.api.AuditEntryInvalidException.class);
    assertThat(countFor(subject)).isZero();
    assertThat(List.of(subject)).hasSize(1);
  }
}
