package com.zellastay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class AuditImmutabilityIT {

  @Autowired AuditRecorder recorder;
  @Autowired JdbcTemplate jdbc;

  private String subject;

  @BeforeEach
  void recordOne() {
    subject = UUID.randomUUID().toString();
    recorder.recordBestEffort(
        new AuditEntry(
            AuditActor.system(),
            TestAuditAction.LOGIN_SUCCESS,
            new AuditSubject("account", subject),
            AuditOutcome.SUCCESS,
            Map.of()));
  }

  @Test
  void rowsCannotBeUpdated() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update audit_event set outcome = 'DENIED' where subject_id = ?", subject))
        .hasMessageContaining("append-only");
  }

  @Test
  void rowsCannotBeDeleted() {
    assertThatThrownBy(() -> jdbc.update("delete from audit_event where subject_id = ?", subject))
        .hasMessageContaining("append-only");
  }

  @Test
  void theTableCannotBeTruncated() {
    assertThatThrownBy(() -> jdbc.execute("truncate audit_event"))
        .hasMessageContaining("append-only");
  }

  @Test
  void aPartitionCannotBeTruncatedDirectly() {
    String partition =
        jdbc.queryForObject(
            "select tableoid::regclass::text from audit_event where subject_id = ?",
            String.class,
            subject);

    assertThat(partition).startsWith("audit_event_y");
    assertThatThrownBy(() -> jdbc.execute("truncate " + partition))
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () -> jdbc.update("delete from " + partition + " where subject_id = ?", subject))
        .hasMessageContaining("append-only");
  }
}
