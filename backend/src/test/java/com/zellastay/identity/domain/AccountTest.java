package com.zellastay.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.IllegalAccountStateException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountTest {

  private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");
  private static final LockoutPolicy LOCKOUT =
      new LockoutPolicy(3, Duration.ofMinutes(1), Duration.ofMinutes(8));

  private static Account pending() {
    return Account.register(
        AccountId.random(),
        AccountKind.CUSTOMER,
        Email.of("jane@example.com"),
        "hash",
        new Profile("Jane", "Doe"),
        "fr",
        T0);
  }

  private static Account active() {
    Account account = pending();
    account.verifyEmail(T0);
    return account;
  }

  @Test
  void registrationStartsPendingVerificationWithoutAVerifiedEmail() {
    Account account = pending();

    assertThat(account.status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    assertThat(account.snapshot().emailVerifiedAt()).isNull();
    assertThat(account.canAuthenticate(T0)).isFalse();
  }

  @Test
  void verifyingTheEmailActivatesThePendingAccount() {
    Account account = pending();

    account.verifyEmail(T0.plusSeconds(5));

    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.snapshot().emailVerifiedAt()).isEqualTo(T0.plusSeconds(5));
    assertThat(account.canAuthenticate(T0)).isTrue();
  }

  @Test
  void verifyingAnAlreadyActiveOrSuspendedAccountIsIllegal() {
    assertThatThrownBy(() -> active().verifyEmail(T0))
        .isInstanceOf(IllegalAccountStateException.class);
    Account suspended = active();
    suspended.suspend();
    assertThatThrownBy(() -> suspended.verifyEmail(T0))
        .isInstanceOf(IllegalAccountStateException.class);
  }

  @Test
  void suspensionBlocksAuthenticationAndReinstatementRestoresIt() {
    Account account = active();

    account.suspend();
    assertThat(account.status()).isEqualTo(AccountStatus.SUSPENDED);
    assertThat(account.canAuthenticate(T0)).isFalse();

    account.reinstate();
    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  void aSuspendedUnverifiedAccountReturnsToPendingVerification() {
    Account account = pending();
    account.suspend();

    account.reinstate();

    assertThat(account.status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
  }

  @Test
  void onlySuspendedAccountsCanBeReinstatedAndOnlyActiveOnesDeactivated() {
    assertThatThrownBy(() -> active().reinstate()).isInstanceOf(IllegalAccountStateException.class);
    assertThatThrownBy(() -> pending().deactivate())
        .isInstanceOf(IllegalAccountStateException.class);
    Account account = active();
    account.deactivate();
    assertThat(account.status()).isEqualTo(AccountStatus.DEACTIVATED);
    assertThatThrownBy(account::suspend).isInstanceOf(IllegalAccountStateException.class);
  }

  @Test
  void lockoutStartsAtTheThresholdDoublesAndIsCapped() {
    Account account = active();

    account.recordFailedLogin(T0, LOCKOUT);
    account.recordFailedLogin(T0, LOCKOUT);
    assertThat(account.isLocked(T0)).isFalse();

    account.recordFailedLogin(T0, LOCKOUT);
    assertThat(account.isLocked(T0.plusSeconds(59))).isTrue();
    assertThat(account.isLocked(T0.plusSeconds(60))).isFalse();

    account.recordFailedLogin(T0, LOCKOUT);
    assertThat(account.snapshot().lockedUntil()).isEqualTo(T0.plus(Duration.ofMinutes(2)));
    for (int i = 0; i < 10; i++) {
      account.recordFailedLogin(T0, LOCKOUT);
    }
    assertThat(account.snapshot().lockedUntil()).isEqualTo(T0.plus(Duration.ofMinutes(8)));
  }

  @Test
  void aLockedAccountCannotAuthenticateUntilTheLockExpires() {
    Account account = active();
    for (int i = 0; i < 3; i++) {
      account.recordFailedLogin(T0, LOCKOUT);
    }

    assertThat(account.canAuthenticate(T0.plusSeconds(10))).isFalse();
    assertThat(account.canAuthenticate(T0.plusSeconds(61))).isTrue();
  }

  @Test
  void aSuccessfulLoginResetsTheFailureCounter() {
    Account account = active();
    account.recordFailedLogin(T0, LOCKOUT);
    account.recordFailedLogin(T0, LOCKOUT);

    account.recordSuccessfulLogin(T0.plusSeconds(1));
    account.recordFailedLogin(T0.plusSeconds(2), LOCKOUT);
    account.recordFailedLogin(T0.plusSeconds(2), LOCKOUT);

    assertThat(account.isLocked(T0.plusSeconds(2))).isFalse();
    assertThat(account.snapshot().lastLoginAt()).isEqualTo(T0.plusSeconds(1));
  }

  @Test
  void changingThePasswordReplacesTheHashAndClearsTheLock() {
    Account account = active();
    for (int i = 0; i < 3; i++) {
      account.recordFailedLogin(T0, LOCKOUT);
    }

    account.changePassword("new-hash", T0.plusSeconds(5));

    assertThat(account.snapshot().passwordHash()).isEqualTo("new-hash");
    assertThat(account.snapshot().passwordChangedAt()).isEqualTo(T0.plusSeconds(5));
    assertThat(account.isLocked(T0.plusSeconds(5))).isFalse();
  }

  @Test
  void aSnapshotRoundTripPreservesEveryField() {
    Account account = active();
    account.recordFailedLogin(T0, LOCKOUT);

    assertThat(Account.restore(account.snapshot()).snapshot()).isEqualTo(account.snapshot());
  }
}
