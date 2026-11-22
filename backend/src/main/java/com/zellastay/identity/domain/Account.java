package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.IllegalAccountStateException;
import java.time.Instant;

/** The account aggregate. State changes only through methods that enforce the lifecycle. */
public final class Account {

  private final AccountId id;
  private final AccountKind kind;
  private final Email email;
  private final Profile profile;
  private final String locale;
  private final Instant createdAt;
  private final long version;

  private AccountStatus status;
  private Instant emailVerifiedAt;
  private String passwordHash;
  private int failedLoginCount;
  private Instant lockedUntil;
  private Instant passwordChangedAt;
  private Instant lastLoginAt;

  private Account(AccountSnapshot s) {
    this.id = s.id();
    this.kind = s.kind();
    this.email = s.email();
    this.profile = s.profile();
    this.locale = s.locale();
    this.createdAt = s.createdAt();
    this.version = s.version();
    this.status = s.status();
    this.emailVerifiedAt = s.emailVerifiedAt();
    this.passwordHash = s.passwordHash();
    this.failedLoginCount = s.failedLoginCount();
    this.lockedUntil = s.lockedUntil();
    this.passwordChangedAt = s.passwordChangedAt();
    this.lastLoginAt = s.lastLoginAt();
  }

  public static Account register(
      AccountId id,
      AccountKind kind,
      Email email,
      String passwordHash,
      Profile profile,
      String locale,
      Instant now) {
    return new Account(
        new AccountSnapshot(
            id,
            kind,
            email,
            null,
            passwordHash,
            AccountStatus.PENDING_VERIFICATION,
            profile,
            locale,
            0,
            null,
            now,
            null,
            now,
            0));
  }

  /** An account created by an administrator: the email is trusted, so it starts active. */
  public static Account registerTrusted(
      AccountId id,
      AccountKind kind,
      Email email,
      String passwordHash,
      Profile profile,
      String locale,
      Instant now) {
    return new Account(
        new AccountSnapshot(
            id,
            kind,
            email,
            now,
            passwordHash,
            AccountStatus.ACTIVE,
            profile,
            locale,
            0,
            null,
            now,
            null,
            now,
            0));
  }

  public static Account restore(AccountSnapshot snapshot) {
    return new Account(snapshot);
  }

  public AccountSnapshot snapshot() {
    return new AccountSnapshot(
        id,
        kind,
        email,
        emailVerifiedAt,
        passwordHash,
        status,
        profile,
        locale,
        failedLoginCount,
        lockedUntil,
        passwordChangedAt,
        lastLoginAt,
        createdAt,
        version);
  }

  public AccountId id() {
    return id;
  }

  public AccountStatus status() {
    return status;
  }

  public boolean isLocked(Instant now) {
    return lockedUntil != null && now.isBefore(lockedUntil);
  }

  /** Accounts that are waiting for verification or active may reset their password. */
  public boolean canResetPassword() {
    return status == AccountStatus.ACTIVE || status == AccountStatus.PENDING_VERIFICATION;
  }

  public boolean canAuthenticate(Instant now) {
    return status == AccountStatus.ACTIVE && !isLocked(now);
  }

  public void verifyEmail(Instant now) {
    require(status == AccountStatus.PENDING_VERIFICATION, "email is already verified");
    status = AccountStatus.ACTIVE;
    emailVerifiedAt = now;
  }

  public void suspend() {
    require(
        status == AccountStatus.ACTIVE || status == AccountStatus.PENDING_VERIFICATION,
        "only active or pending accounts can be suspended");
    status = AccountStatus.SUSPENDED;
  }

  public void reinstate() {
    require(status == AccountStatus.SUSPENDED, "only suspended accounts can be reinstated");
    status = emailVerifiedAt == null ? AccountStatus.PENDING_VERIFICATION : AccountStatus.ACTIVE;
  }

  public void deactivate() {
    require(status == AccountStatus.ACTIVE, "only active accounts can be deactivated");
    status = AccountStatus.DEACTIVATED;
  }

  public void recordFailedLogin(Instant now, LockoutPolicy policy) {
    failedLoginCount++;
    var lock = policy.lockFor(failedLoginCount);
    if (!lock.isZero()) {
      lockedUntil = now.plus(lock);
    }
  }

  public void recordSuccessfulLogin(Instant now) {
    failedLoginCount = 0;
    lockedUntil = null;
    lastLoginAt = now;
  }

  public void changePassword(String newHash, Instant now) {
    require(
        status == AccountStatus.ACTIVE || status == AccountStatus.PENDING_VERIFICATION,
        "the password cannot be changed in this state");
    passwordHash = newHash;
    passwordChangedAt = now;
    failedLoginCount = 0;
    lockedUntil = null;
  }

  /** Replaces the hash after a parameter upgrade; not a password change. */
  public void rehashPassword(String newHash) {
    passwordHash = newHash;
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalAccountStateException(message);
    }
  }
}
