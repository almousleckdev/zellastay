package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountNotFoundException;
import com.zellastay.identity.api.CurrentPasswordInvalidException;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Lets a customer or vendor user close their own account after re-entering the password. */
@Service
public class DeactivateAccountService {

  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final SessionRevoker revoker;
  private final IdentityEvents events;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;

  DeactivateAccountService(
      AccountRepository accounts,
      PasswordHasher hasher,
      SessionRevoker revoker,
      IdentityEvents events,
      IdentityAuditTrail trail,
      TransactionTemplate transaction) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.revoker = revoker;
    this.events = events;
    this.trail = trail;
    this.transaction = transaction;
  }

  public void deactivate(CurrentPrincipal principal, String password) {
    Account loaded =
        accounts.findById(principal.accountId()).orElseThrow(AccountNotFoundException::new);
    if (!hasher.matches(password, loaded.snapshot().passwordHash())) {
      throw new CurrentPasswordInvalidException();
    }
    transaction.executeWithoutResult(
        status -> {
          Account account =
              accounts.findById(principal.accountId()).orElseThrow(AccountNotFoundException::new);
          account.deactivate();
          accounts.update(account);
          revoker.revokeAll(account.id(), "account-deactivated");
          events.deactivated(account.id().value());
          trail.succeeded(account, IdentityAuditAction.ACCOUNT_DEACTIVATED);
        });
  }
}
