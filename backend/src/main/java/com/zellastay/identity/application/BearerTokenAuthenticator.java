package com.zellastay.identity.application;

import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.shared.security.TokenAudience;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Authenticates the access token of a request for one audience. A token is accepted only if its
 * signature, expiry and audience verify and its session is still usable; otherwise the answer is
 * empty, without saying which check failed.
 */
@Service
public class BearerTokenAuthenticator {

  private final AccessTokens tokens;
  private final SessionGuard sessions;
  private final Clock clock;

  BearerTokenAuthenticator(AccessTokens tokens, SessionGuard sessions, Clock clock) {
    this.tokens = tokens;
    this.sessions = sessions;
    this.clock = clock;
  }

  public Optional<CurrentPrincipal> authenticate(String token, TokenAudience audience) {
    return tokens
        .parse(token, audience, clock.instant())
        .filter(claims -> sessions.isUsable(claims.sessionId()))
        .map(
            claims ->
                new CurrentPrincipal(
                    claims.accountId(),
                    claims.audience(),
                    claims.sessionId(),
                    claims.mfaVerified()));
  }
}
