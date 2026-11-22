package com.zellastay.identity.application;

import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.api.SessionNotFoundException;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Lets the signed-in user see and end their own sessions (and nobody else's). */
@Service
public class SessionManagementService {

  private final SessionRepository sessions;
  private final SessionRevoker revoker;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;
  private final Clock clock;

  SessionManagementService(
      SessionRepository sessions,
      SessionRevoker revoker,
      IdentityAuditTrail trail,
      TransactionTemplate transaction,
      Clock clock) {
    this.sessions = sessions;
    this.revoker = revoker;
    this.trail = trail;
    this.transaction = transaction;
    this.clock = clock;
  }

  public List<SessionSummary> list(CurrentPrincipal principal) {
    return sessions.findActiveByAccount(principal.accountId(), clock.instant()).stream()
        .map(
            session ->
                new SessionSummary(
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
    trail.succeededBestEffort(
        principal, IdentityAuditAction.SESSION_REVOKED, Map.of("reason", reason));
  }
}
