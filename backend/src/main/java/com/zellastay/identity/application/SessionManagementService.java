package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.api.SessionNotFoundException;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Lets the signed-in user see and end their own sessions (and nobody else's). */
@Service
public class SessionManagementService {

  public record SessionView(UUID id, Instant createdAt, Instant lastUsedAt, boolean current) {}

  private final SessionRepository sessions;
  private final SessionRevoker revoker;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  SessionManagementService(
      SessionRepository sessions,
      SessionRevoker revoker,
      AuditRecorder audit,
      TransactionTemplate transaction,
      Clock clock) {
    this.sessions = sessions;
    this.revoker = revoker;
    this.audit = audit;
    this.transaction = transaction;
    this.clock = clock;
  }

  public List<SessionView> list(CurrentPrincipal principal) {
    return sessions.findActiveByAccount(principal.accountId(), clock.instant()).stream()
        .map(
            session ->
                new SessionView(
                    session.id(),
                    session.createdAt(),
                    session.lastUsedAt(),
                    session.id().equals(principal.sessionId())))
        .toList();
  }

  public void revoke(CurrentPrincipal principal, UUID sessionId) {
    transaction.executeWithoutResult(
        status -> {
          Session session =
              sessions
                  .findById(sessionId)
                  .filter(found -> found.accountId().equals(principal.accountId()))
                  .orElseThrow(SessionNotFoundException::new);
          revoker.revoke(session, "revoked-by-user");
        });
    record(principal, "revoked-by-user");
  }

  public void revokeAll(CurrentPrincipal principal) {
    transaction.executeWithoutResult(
        status -> revoker.revokeAll(principal.accountId(), "signed-out-everywhere"));
    record(principal, "signed-out-everywhere");
  }

  private void record(CurrentPrincipal principal, String reason) {
    audit.recordBestEffort(
        new AuditEntry(
            actor(principal),
            IdentityAuditAction.SESSION_REVOKED,
            new AuditSubject("account", principal.accountId().value().toString()),
            AuditOutcome.SUCCESS,
            Map.of("reason", reason)));
  }

  static AuditActor actor(CurrentPrincipal principal) {
    UUID id = principal.accountId().value();
    return switch (principal.kind()) {
      case CUSTOMER -> AuditActor.customer(id);
      case VENDOR_USER -> AuditActor.vendorUser(id, null);
      case ADMIN -> AuditActor.admin(id);
    };
  }
}
