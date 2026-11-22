package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountNotFoundException;
import com.zellastay.identity.api.CurrentPasswordInvalidException;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.LockoutPolicy;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Changes the signed-in user's password; every other session is signed out. */
@Service
public class ChangePasswordService {

  private static final Logger LOG = LoggerFactory.getLogger(ChangePasswordService.class);

  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final PasswordPolicy policy;
  private final LockoutPolicy lockout;
  private final SessionRevoker revoker;
  private final QuietDelivery delivery;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;
  private final Clock clock;

  ChangePasswordService(
      AccountRepository accounts,
      PasswordHasher hasher,
      PasswordPolicy policy,
      LockoutPolicy lockout,
      SessionRevoker revoker,
      QuietDelivery delivery,
      IdentityAuditTrail trail,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.policy = policy;
    this.lockout = lockout;
    this.revoker = revoker;
    this.delivery = delivery;
    this.trail = trail;
    this.transaction = transaction;
    this.clock = clock;
  }

  public void change(CurrentPrincipal principal, String currentPassword, String newPassword) {
    Account account =
        accounts.findById(principal.accountId()).orElseThrow(AccountNotFoundException::new);
    if (!hasher.matches(currentPassword, account.snapshot().passwordHash())) {
      countFailure(account);
      throw new CurrentPasswordInvalidException();
    }
    policy.check(newPassword, account.snapshot().email());
    String hash = hasher.hash(newPassword);
    transaction.executeWithoutResult(status -> apply(principal, hash));
    delivery.send(
        new DeliveryRequest(
            DeliveryTemplate.PASSWORD_CHANGED,
            account.snapshot().email().value(),
            account.snapshot().locale(),
            null));
  }

  private void apply(CurrentPrincipal principal, String hash) {
    Account account =
        accounts.findById(principal.accountId()).orElseThrow(AccountNotFoundException::new);
    Instant now = clock.instant();
    account.changePassword(hash, now);
    accounts.update(account);
    revoker.revokeAllExcept(account.id(), principal.sessionId(), "password-changed");
    trail.succeeded(account, IdentityAuditAction.PASSWORD_CHANGED);
  }

  private void countFailure(Account loaded) {
    try {
      transaction.executeWithoutResult(
          status -> {
            Account account = accounts.findById(loaded.id()).orElseThrow();
            account.recordFailedLogin(clock.instant(), lockout);
            accounts.update(account);
          });
    } catch (OptimisticLockingFailureException concurrent) {
      LOG.info("a concurrent update raced with counting a failed password change");
    }
  }
}
