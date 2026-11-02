package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.identity.domain.SessionStatusCache;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Answers "may this session still be used?" for every authenticated request. */
@Service
public class SessionGuard {

  private final SessionStatusCache cache;
  private final SessionRepository sessions;
  private final AccountRepository accounts;
  private final Clock clock;

  SessionGuard(
      SessionStatusCache cache,
      SessionRepository sessions,
      AccountRepository accounts,
      Clock clock) {
    this.cache = cache;
    this.sessions = sessions;
    this.accounts = accounts;
    this.clock = clock;
  }

  public boolean isUsable(UUID sessionId) {
    Optional<Boolean> cached = cache.get(sessionId);
    if (cached.isPresent()) {
      return cached.get();
    }
    boolean usable = lookup(sessionId);
    cache.put(sessionId, usable);
    return usable;
  }

  void evict(UUID sessionId) {
    cache.evict(sessionId);
  }

  private boolean lookup(UUID sessionId) {
    Optional<Session> session = sessions.findById(sessionId);
    return session.isPresent()
        && session.get().isActive(clock.instant())
        && accounts
            .findById(session.get().accountId())
            .map(account -> account.status() == AccountStatus.ACTIVE)
            .orElse(false);
  }
}
