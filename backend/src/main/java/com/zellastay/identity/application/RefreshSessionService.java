package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.RefreshInvalidException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.RefreshRotation;
import com.zellastay.identity.domain.RefreshRotation.Decision;
import com.zellastay.identity.domain.RefreshToken;
import com.zellastay.identity.domain.RefreshTokenRepository;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.identity.domain.TokenSettings;
import com.zellastay.shared.security.TokenAudience;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Rotates refresh tokens; reuse of a stale token revokes the whole session. */
@Service
public class RefreshSessionService {

  private record Outcome(TokenPair pair, Account reusedBy) {}

  private final RefreshTokenRepository refreshTokens;
  private final SessionRepository sessions;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final SessionIssuer issuer;
  private final SessionRevoker revoker;
  private final TokenSettings settings;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  RefreshSessionService(
      RefreshTokenRepository refreshTokens,
      SessionRepository sessions,
      AccountRepository accounts,
      SecretHasher hasher,
      SessionIssuer issuer,
      SessionRevoker revoker,
      TokenSettings settings,
      AuditRecorder audit,
      TransactionTemplate transaction,
      Clock clock) {
    this.refreshTokens = refreshTokens;
    this.sessions = sessions;
    this.accounts = accounts;
    this.hasher = hasher;
    this.issuer = issuer;
    this.revoker = revoker;
    this.settings = settings;
    this.audit = audit;
    this.transaction = transaction;
    this.clock = clock;
  }

  public TokenPair refresh(TokenAudience audience, String presented) {
    if (presented == null || presented.isBlank()) {
      throw new RefreshInvalidException();
    }
    Outcome outcome = transaction.execute(status -> rotate(audience, hasher.digest(presented)));
    if (outcome == null || outcome.pair() == null) {
      if (outcome != null && outcome.reusedBy() != null) {
        audit.recordBestEffort(
            new AuditEntry(
                AuditActors.actor(outcome.reusedBy()),
                IdentityAuditAction.REFRESH_REUSE_DETECTED,
                AuditActors.subject(outcome.reusedBy()),
                AuditOutcome.DENIED,
                Map.of()));
      }
      throw new RefreshInvalidException();
    }
    return outcome.pair();
  }

  private Outcome rotate(TokenAudience audience, String hash) {
    Optional<RefreshToken> token = refreshTokens.findByHash(hash);
    Optional<Session> session = token.flatMap(t -> sessions.findById(t.sessionId()));
    if (session.isEmpty() || session.get().audience() != audience) {
      return rejected();
    }
    Optional<Account> account = accounts.findById(session.get().accountId());
    if (account.isEmpty() || account.get().status() != AccountStatus.ACTIVE) {
      revokeIfActive(session.get(), "account-inactive");
      return rejected();
    }
    Instant now = clock.instant();
    Decision decision =
        RefreshRotation.decide(session.get(), token.get(), now, settings.refreshReuseGrace());
    return switch (decision) {
      case ROTATE -> rotateFrom(token.get(), session.get(), now);
      case RETRY_WITHIN_GRACE -> retry(token.get(), session.get(), account.get(), now);
      case REUSED -> reused(session.get(), account.get());
      case INVALID -> rejected();
    };
  }

  private Outcome rotateFrom(RefreshToken token, Session session, Instant now) {
    UUID next = UUID.randomUUID();
    if (!refreshTokens.markUsed(token.id(), now, next)) {
      return rejected();
    }
    return issued(session, next, now);
  }

  private Outcome retry(RefreshToken token, Session session, Account account, Instant now) {
    Optional<RefreshToken> replacement = refreshTokens.findById(token.replacedBy());
    if (replacement.isEmpty() || replacement.get().usedAt() != null) {
      return reused(session, account);
    }
    UUID next = UUID.randomUUID();
    if (!refreshTokens.markUsed(replacement.get().id(), now, next)) {
      return rejected();
    }
    return issued(session, next, now);
  }

  private Outcome issued(Session session, UUID tokenId, Instant now) {
    session.touch(now);
    sessions.update(session);
    return new Outcome(issuer.issueFor(session, tokenId), null);
  }

  private Outcome reused(Session session, Account account) {
    revokeIfActive(session, "refresh-reuse");
    return new Outcome(null, account);
  }

  private void revokeIfActive(Session session, String reason) {
    if (session.revokedAt() == null) {
      revoker.revoke(session, reason);
    }
  }

  private static Outcome rejected() {
    return new Outcome(null, null);
  }
}
