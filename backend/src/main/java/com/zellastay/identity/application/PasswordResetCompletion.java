package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Applies a verified reset: new password, verified email, every session signed out, audit. */
@Component
class PasswordResetCompletion {

  private final AccountRepository accounts;
  private final SessionRevoker revoker;
  private final IdentityAuditTrail trail;

  PasswordResetCompletion(
      AccountRepository accounts, SessionRevoker revoker, IdentityAuditTrail trail) {
    this.accounts = accounts;
    this.revoker = revoker;
    this.trail = trail;
  }

  void complete(Account account, String newPasswordHash, Instant now) {
    if (account.status() == AccountStatus.PENDING_VERIFICATION) {
      account.verifyEmail(now);
    }
    account.changePassword(newPasswordHash, now);
    accounts.update(account);
    revoker.revokeAll(account.id(), "password-reset");
    trail.succeeded(account, IdentityAuditAction.PASSWORD_RESET_COMPLETED);
  }
}
