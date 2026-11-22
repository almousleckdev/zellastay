package com.zellastay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.audit.application.AuditQueryService;
import com.zellastay.audit.domain.AuditPage;
import com.zellastay.audit.domain.AuditQuery;
import com.zellastay.audit.domain.AuditRecord;
import com.zellastay.audit.domain.InvalidAuditQueryException;
import com.zellastay.support.IntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class AuditQueryIT {

  @Autowired AuditRecorder recorder;
  @Autowired AuditQueryService queries;

  private void record(UUID actor, TestAuditAction action, String subject) {
    recorder.recordBestEffort(
        new AuditEntry(
            AuditActor.admin(actor),
            action,
            new AuditSubject("account", subject),
            AuditOutcome.SUCCESS,
            action == TestAuditAction.LOGIN_SUCCESS ? Map.of("method", "password") : Map.of()));
  }

  private static AuditQuery forActor(UUID actor, String cursor, int limit) {
    return new AuditQuery(actor, null, null, null, null, null, cursor, limit);
  }

  @Test
  void pagesThroughAllEntriesNewestFirstWithoutDuplicates() {
    UUID actor = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      record(actor, TestAuditAction.LOGIN_SUCCESS, UUID.randomUUID().toString());
    }

    List<AuditRecord> seen = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      AuditPage page = queries.find(forActor(actor, cursor, 2));
      assertThat(page.items().size()).isLessThanOrEqualTo(2);
      seen.addAll(page.items());
      cursor = page.nextCursor();
      pages++;
    } while (cursor != null && pages < 10);

    assertThat(pages).isEqualTo(3);
    assertThat(seen).hasSize(5);
    assertThat(seen.stream().map(AuditRecord::id).distinct()).hasSize(5);
    assertThat(seen).isSortedAccordingTo((a, b) -> b.occurredAt().compareTo(a.occurredAt()));
    assertThat(seen.get(0).details()).containsEntry("method", "password");
    assertThat(seen.get(0).actorId()).isEqualTo(actor);
  }

  @Test
  void filtersByActionAndSubject() {
    UUID actor = UUID.randomUUID();
    String subject = UUID.randomUUID().toString();
    record(actor, TestAuditAction.LOGIN_SUCCESS, subject);
    record(actor, TestAuditAction.ROLE_GRANTED, subject);
    record(actor, TestAuditAction.ROLE_GRANTED, UUID.randomUUID().toString());

    AuditPage byAction =
        queries.find(new AuditQuery(actor, "admin.role.granted", null, null, null, null, null, 50));
    AuditPage bySubject =
        queries.find(new AuditQuery(actor, null, "account", subject, null, null, null, 50));

    assertThat(byAction.items()).hasSize(2);
    assertThat(bySubject.items()).hasSize(2);
    assertThat(byAction.nextCursor()).isNull();
  }

  @Test
  void filtersByDateRange() {
    UUID actor = UUID.randomUUID();
    record(actor, TestAuditAction.ROLE_GRANTED, UUID.randomUUID().toString());

    AuditPage future =
        queries.find(
            new AuditQuery(
                actor, null, null, null, Instant.now().plusSeconds(3600), null, null, 50));
    AuditPage past =
        queries.find(
            new AuditQuery(
                actor, null, null, null, null, Instant.now().minusSeconds(3600), null, 50));

    assertThat(future.items()).isEmpty();
    assertThat(past.items()).isEmpty();
  }

  @Test
  void rejectsBadLimitsAndCursors() {
    UUID actor = UUID.randomUUID();

    assertThatThrownBy(() -> forActor(actor, null, 0))
        .isInstanceOf(InvalidAuditQueryException.class);
    assertThatThrownBy(() -> forActor(actor, null, 101))
        .isInstanceOf(InvalidAuditQueryException.class);
    assertThatThrownBy(() -> queries.find(forActor(actor, "not-a-cursor", 10)))
        .isInstanceOf(InvalidAuditQueryException.class);
  }
}
