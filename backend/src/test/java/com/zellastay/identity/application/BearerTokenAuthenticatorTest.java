package com.zellastay.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.identity.domain.TokenClaims;
import com.zellastay.shared.security.TokenAudience;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BearerTokenAuthenticatorTest {

  private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

  private final AccessTokens tokens = mock(AccessTokens.class);
  private final SessionGuard sessions = mock(SessionGuard.class);
  private final BearerTokenAuthenticator authenticator =
      new BearerTokenAuthenticator(tokens, sessions, Clock.fixed(NOW, ZoneOffset.UTC));

  private final AccountId account = AccountId.random();
  private final UUID session = UUID.randomUUID();
  private final TokenClaims claims =
      new TokenClaims(account, TokenAudience.ADMIN, session, true, NOW.plusSeconds(60));

  @Test
  void aValidTokenWithAUsableSessionBecomesAPrincipal() {
    when(tokens.parse("jwt", TokenAudience.ADMIN, NOW)).thenReturn(Optional.of(claims));
    when(sessions.isUsable(session)).thenReturn(true);

    Optional<CurrentPrincipal> principal = authenticator.authenticate("jwt", TokenAudience.ADMIN);

    assertThat(principal)
        .contains(new CurrentPrincipal(account, TokenAudience.ADMIN, session, true));
  }

  @Test
  void aTokenWhoseSessionIsNoLongerUsableIsRejected() {
    when(tokens.parse("jwt", TokenAudience.ADMIN, NOW)).thenReturn(Optional.of(claims));
    when(sessions.isUsable(session)).thenReturn(false);

    assertThat(authenticator.authenticate("jwt", TokenAudience.ADMIN)).isEmpty();
  }

  @Test
  void aTokenThatDoesNotVerifyNeverReachesTheSessionCheck() {
    when(tokens.parse(any(), eq(TokenAudience.CUSTOMER), eq(NOW))).thenReturn(Optional.empty());

    assertThat(authenticator.authenticate("forged", TokenAudience.CUSTOMER)).isEmpty();
    verifyNoInteractions(sessions);
  }
}
