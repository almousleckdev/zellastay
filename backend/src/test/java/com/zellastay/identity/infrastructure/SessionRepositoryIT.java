package com.zellastay.identity.infrastructure;

import static com.zellastay.support.AccountFixtures.uniqueEmail;
import static com.zellastay.support.RepositoryTestData.NOW;
import static com.zellastay.support.RepositoryTestData.newAccount;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.RefreshToken;
import com.zellastay.identity.domain.RefreshTokenRepository;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionLifetime;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.shared.security.TokenAudience;
import com.zellastay.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Persistence of sessions and refresh tokens. */
@IntegrationTest
class SessionRepositoryIT {

  @Autowired AccountRepository accounts;
  @Autowired SessionRepository sessions;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired TransactionTemplate transaction;

  @Test
  void sessionsAndRefreshTokensRoundTripAndTokensAreUsedAtMostOnce() {
    Account account = newAccount(AccountKind.CUSTOMER, uniqueEmail());
    transaction.executeWithoutResult(s -> accounts.insert(account));
    Session session =
        Session.start(
            UUID.randomUUID(),
            account.id(),
            TokenAudience.CUSTOMER,
            false,
            NOW,
            new SessionLifetime(Duration.ofDays(30), Duration.ofDays(14)));
    RefreshToken token =
        new RefreshToken(
            UUID.randomUUID(), session.id(), "hash-" + UUID.randomUUID(), NOW, null, null);
    transaction.executeWithoutResult(
        s -> {
          sessions.insert(session);
          refreshTokens.insert(token);
        });

    Session loaded = sessions.findById(session.id()).orElseThrow();
    RefreshToken byHash = refreshTokens.findByHash(token.tokenHash()).orElseThrow();
    UUID replacement = UUID.randomUUID();

    assertThat(loaded.isActive(NOW)).isTrue();
    assertThat(byHash.id()).isEqualTo(token.id());
    assertThat(refreshTokens.markUsed(token.id(), NOW, replacement)).isTrue();
    assertThat(refreshTokens.markUsed(token.id(), NOW, replacement)).isFalse();
  }

  @Test
  void revokingAllSessionsOfAnAccountLeavesNoneActive() {
    Account account = newAccount(AccountKind.CUSTOMER, uniqueEmail());
    transaction.executeWithoutResult(s -> accounts.insert(account));
    for (int i = 0; i < 2; i++) {
      Session session =
          Session.start(
              UUID.randomUUID(),
              account.id(),
              TokenAudience.CUSTOMER,
              false,
              NOW,
              new SessionLifetime(Duration.ofDays(30), Duration.ofDays(14)));
      transaction.executeWithoutResult(s -> sessions.insert(session));
    }
    assertThat(sessions.findActiveByAccount(account.id(), NOW)).hasSize(2);

    transaction.executeWithoutResult(s -> sessions.revokeAllForAccount(account.id(), "test", NOW));

    assertThat(sessions.findActiveByAccount(account.id(), NOW)).isEmpty();
  }
}
