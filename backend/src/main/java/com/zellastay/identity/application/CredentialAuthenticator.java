package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.AccountSuspendedException;
import com.zellastay.identity.api.EmailInvalidException;
import com.zellastay.identity.api.InvalidCredentialsException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.LockoutPolicy;
import com.zellastay.identity.domain.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checks email and password for one audience. Every failure is the same {@link
 * InvalidCredentialsException}, and unknown accounts cost the same time as known ones; only a
 * suspended account that supplied the right password is told so.
 */
@Component
class CredentialAuthenticator {

  private static final int ATTEMPTS = 3;

  private enum Outcome {
    OK,
    WRONG_PASSWORD,
    LOCKED_NOW,
    LOCKED,
    INACTIVE,
    SUSPENDED
  }

  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final LockoutPolicy lockout;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;
  private final Clock clock;

  CredentialAuthenticator(
      AccountRepository accounts,
      PasswordHasher hasher,
      LockoutPolicy lockout,
      IdentityAuditTrail trail,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.lockout = lockout;
    this.trail = trail;
    this.transaction = transaction;
    this.clock = clock;
  }

  /** Returns the account after a successful check; throws otherwise. */
  Account authenticate(AccountKind kind, String rawEmail, String password) {
    Optional<Email> email = parse(rawEmail);
    Optional<Account> found = email.flatMap(value -> accounts.findByEmail(kind, value));
    if (found.isEmpty()) {
      hasher.burn(password);
      unknownAccountFailed(kind, email.isPresent() ? "unknown" : "invalid-email");
      throw new InvalidCredentialsException();
    }
    Account account = found.get();
    boolean matches = hasher.matches(password, account.snapshot().passwordHash());
    Outcome outcome = settle(account, matches, password);
    return switch (outcome) {
      case OK -> accounts.findById(account.id()).orElseThrow();
      case SUSPENDED -> {
        failed(account, kind, "suspended");
        throw new AccountSuspendedException();
      }
      default -> {
        failed(account, kind, outcome.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        if (outcome == Outcome.LOCKED_NOW) {
          lockoutAudit(account, kind);
        }
        throw new InvalidCredentialsException();
      }
    };
  }

  private Outcome settle(Account loaded, boolean matches, String password) {
    OptimisticLockingFailureException last = null;
    for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
      try {
        return transaction.execute(status -> apply(loaded, matches, password));
      } catch (OptimisticLockingFailureException conflict) {
        last = conflict;
      }
    }
    throw last;
  }

  private Outcome apply(Account loaded, boolean matches, String password) {
    Account account = accounts.findById(loaded.id()).orElseThrow();
    Instant now = clock.instant();
    if (!matches) {
      boolean wasLocked = account.isLocked(now);
      account.recordFailedLogin(now, lockout);
      accounts.update(account);
      return !wasLocked && account.isLocked(now) ? Outcome.LOCKED_NOW : Outcome.WRONG_PASSWORD;
    }
    if (account.status() == AccountStatus.SUSPENDED) {
      return Outcome.SUSPENDED;
    }
    if (account.status() != AccountStatus.ACTIVE) {
      return Outcome.INACTIVE;
    }
    if (account.isLocked(now)) {
      return Outcome.LOCKED;
    }
    account.recordSuccessfulLogin(now);
    if (hasher.needsRehash(account.snapshot().passwordHash())) {
      account.rehashPassword(hasher.hash(password));
    }
    accounts.update(account);
    return Outcome.OK;
  }

  private Optional<Email> parse(String rawEmail) {
    try {
      return Optional.of(Email.of(rawEmail));
    } catch (EmailInvalidException invalid) {
      return Optional.empty();
    }
  }

  private void unknownAccountFailed(AccountKind kind, String reason) {
    trail.anonymousBestEffort(
        IdentityAuditAction.LOGIN_FAILURE, AuditOutcome.FAILURE, failureDetails(kind, reason));
  }

  private void failed(Account account, AccountKind kind, String reason) {
    trail.failedBestEffort(
        account, IdentityAuditAction.LOGIN_FAILURE, failureDetails(kind, reason));
  }

  private static Map<String, Object> failureDetails(AccountKind kind, String reason) {
    return Map.of("audience", kind.audience().value(), "reason", reason);
  }

  private void lockoutAudit(Account account, AccountKind kind) {
    trail.deniedBestEffort(
        account, IdentityAuditAction.LOGIN_LOCKOUT, Map.of("audience", kind.audience().value()));
  }
}
