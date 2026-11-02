package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.Profile;
import com.zellastay.identity.domain.RefreshToken;
import com.zellastay.identity.domain.RefreshTokenRepository;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionLifetime;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.identity.domain.VerificationChallenge;
import com.zellastay.shared.security.TokenAudience;
import com.zellastay.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class IdentityRepositoriesIT {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

  @Autowired AccountRepository accounts;
  @Autowired SessionRepository sessions;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired ChallengeRepository challenges;
  @Autowired TransactionTemplate transaction;

  private static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }

  private static Account newAccount(AccountKind kind, String email) {
    return Account.register(
        AccountId.random(), kind, Email.of(email), "hash", new Profile("Jane", "Doe"), "fr", NOW);
  }

  @Test
  void anAccountRoundTripsWithItsProfile() {
    Account account = newAccount(AccountKind.CUSTOMER, uniqueEmail());
    transaction.executeWithoutResult(s -> accounts.insert(account));

    Account loaded = accounts.findById(account.id()).orElseThrow();

    assertThat(loaded.snapshot()).isEqualTo(account.snapshot());
    assertThat(loaded.snapshot().profile()).isEqualTo(new Profile("Jane", "Doe"));
  }

  @Test
  void anEmailIsUniquePerKindButMayExistInDifferentKinds() {
    String email = uniqueEmail();
    transaction.executeWithoutResult(s -> accounts.insert(newAccount(AccountKind.CUSTOMER, email)));
    transaction.executeWithoutResult(
        s -> accounts.insert(newAccount(AccountKind.VENDOR_USER, email)));

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    s -> accounts.insert(newAccount(AccountKind.CUSTOMER, email))))
        .isInstanceOf(DuplicateKeyException.class);
    assertThat(accounts.findByEmail(AccountKind.CUSTOMER, Email.of(email))).isPresent();
    assertThat(accounts.findByEmail(AccountKind.ADMIN, Email.of(email))).isEmpty();
  }

  @Test
  void concurrentRegistrationsOfTheSameEmailCreateExactlyOneAccount() throws Exception {
    String email = uniqueEmail();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Boolean>> attempts =
          java.util.stream.IntStream.range(0, 8)
              .<Callable<Boolean>>mapToObj(
                  i ->
                      () -> {
                        try {
                          transaction.executeWithoutResult(
                              s -> accounts.insert(newAccount(AccountKind.CUSTOMER, email)));
                          return true;
                        } catch (DuplicateKeyException e) {
                          return false;
                        }
                      })
              .toList();
      long winners = 0;
      for (Future<Boolean> result : pool.invokeAll(attempts)) {
        winners += result.get() ? 1 : 0;
      }
      assertThat(winners).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void updatesAreOptimisticallyLocked() {
    Account account = newAccount(AccountKind.CUSTOMER, uniqueEmail());
    transaction.executeWithoutResult(s -> accounts.insert(account));
    Account first = accounts.findById(account.id()).orElseThrow();
    Account second = accounts.findById(account.id()).orElseThrow();
    first.verifyEmail(NOW);
    second.suspend();

    transaction.executeWithoutResult(s -> accounts.update(first));

    assertThatThrownBy(() -> transaction.executeWithoutResult(s -> accounts.update(second)))
        .isInstanceOf(OptimisticLockingFailureException.class);
    Account stored = accounts.findById(account.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(stored.snapshot().version()).isEqualTo(1);
  }

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

  @Test
  void challengesRoundTripAndAreCountedPerWindow() {
    Account account = newAccount(AccountKind.CUSTOMER, uniqueEmail());
    transaction.executeWithoutResult(s -> accounts.insert(account));
    VerificationChallenge challenge =
        VerificationChallenge.issue(
            UUID.randomUUID(),
            account.id(),
            ChallengePurpose.EMAIL_VERIFICATION,
            "hash",
            NOW,
            Duration.ofMinutes(10));
    transaction.executeWithoutResult(s -> challenges.insert(challenge));

    VerificationChallenge loaded =
        challenges.findLatest(account.id(), ChallengePurpose.EMAIL_VERIFICATION).orElseThrow();
    loaded.check(false, NOW, 5);
    transaction.executeWithoutResult(s -> challenges.update(loaded));

    assertThat(challenges.findById(challenge.id()).orElseThrow().attempts()).isEqualTo(1);
    assertThat(
            challenges.countIssuedSince(
                account.id(), ChallengePurpose.EMAIL_VERIFICATION, NOW.minusSeconds(60)))
        .isEqualTo(1);
    assertThat(challenges.lastIssuedAt(account.id(), ChallengePurpose.EMAIL_VERIFICATION))
        .contains(NOW);
    assertThat(challenges.findLatest(account.id(), ChallengePurpose.PASSWORD_RESET)).isEmpty();
  }
}
