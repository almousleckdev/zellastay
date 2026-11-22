package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The one place sessions are revoked, so the cache is always evicted (after commit). */
@Component
class SessionRevoker {

  private final SessionRepository sessions;
  private final SessionGuard guard;
  private final Clock clock;

  SessionRevoker(SessionRepository sessions, SessionGuard guard, Clock clock) {
    this.sessions = sessions;
    this.guard = guard;
    this.clock = clock;
  }

  void revoke(Session session, String reason) {
    session.revoke(reason, clock.instant());
    sessions.update(session);
    evictAfterCommit(List.of(session.id()));
  }

  void revokeAll(AccountId accountId, String reason) {
    revokeAllExcept(accountId, null, reason);
  }

  void revokeAllExcept(AccountId accountId, UUID keep, String reason) {
    Instant now = clock.instant();
    List<UUID> ids =
        sessions.findActiveByAccount(accountId, now).stream()
            .map(Session::id)
            .filter(id -> !id.equals(keep))
            .toList();
    sessions.revokeAllForAccountExcept(accountId, keep, reason, now);
    evictAfterCommit(ids);
  }

  private void evictAfterCommit(List<UUID> ids) {
    AfterCommit.run(() -> ids.forEach(guard::evict));
  }
}
