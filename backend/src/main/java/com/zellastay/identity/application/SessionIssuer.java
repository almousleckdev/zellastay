package com.zellastay.identity.application;

import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.IssuedToken;
import com.zellastay.identity.domain.RefreshToken;
import com.zellastay.identity.domain.RefreshTokenRepository;
import com.zellastay.identity.domain.SecretGenerator;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.identity.domain.TokenSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Starts sessions and mints tokens. Must be called inside the caller's transaction. */
@Component
class SessionIssuer {

  private final SessionRepository sessions;
  private final RefreshTokenRepository refreshTokens;
  private final AccessTokens accessTokens;
  private final SecretGenerator generator;
  private final SecretHasher hasher;
  private final TokenSettings settings;
  private final Clock clock;

  SessionIssuer(
      SessionRepository sessions,
      RefreshTokenRepository refreshTokens,
      AccessTokens accessTokens,
      SecretGenerator generator,
      SecretHasher hasher,
      TokenSettings settings,
      Clock clock) {
    this.sessions = sessions;
    this.refreshTokens = refreshTokens;
    this.accessTokens = accessTokens;
    this.generator = generator;
    this.hasher = hasher;
    this.settings = settings;
    this.clock = clock;
  }

  TokenPair start(Account account, boolean mfaVerified) {
    Instant now = clock.instant();
    var audience = account.snapshot().kind().audience();
    Session session =
        Session.start(
            UUID.randomUUID(),
            account.id(),
            audience,
            mfaVerified,
            now,
            settings.lifetime(audience));
    sessions.insert(session);
    return issueFor(session, UUID.randomUUID());
  }

  /** New refresh token (with the given id) plus access token for an existing session. */
  TokenPair issueFor(Session session, UUID refreshTokenId) {
    Instant now = clock.instant();
    String secret = generator.token();
    refreshTokens.insert(
        new RefreshToken(refreshTokenId, session.id(), hasher.digest(secret), now, null, null));
    IssuedToken access =
        accessTokens.issue(
            session.accountId(), session.audience(), session.id(), session.mfaVerified(), now);
    return new TokenPair(
        access.value(), access.expiresAt(), settings.accessTtl().toSeconds(), secret);
  }
}
