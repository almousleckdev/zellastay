package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountNotFoundException;
import com.zellastay.identity.api.AdminAccessApi;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.api.IllegalAccountStateException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Suspends and reinstates accounts. Suspension signs the account out of every session. */
@Service
public class AccountSuspensionService {

  private final AdminAccessApi access;
  private final AccountRepository accounts;
  private final SessionRevoker revoker;
  private final IdentityEvents events;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;

  AccountSuspensionService(
      AdminAccessApi access,
      AccountRepository accounts,
      SessionRevoker revoker,
      IdentityEvents events,
      IdentityAuditTrail trail,
      TransactionTemplate transaction) {
    this.access = access;
    this.accounts = accounts;
    this.revoker = revoker;
    this.events = events;
    this.trail = trail;
    this.transaction = transaction;
  }

  public void suspend(CurrentPrincipal actor, AccountId target, String reasonCode) {
    access.require(actor, AdminPermission.ACCOUNT_SUSPEND);
    if (actor.accountId().equals(target)) {
      throw new IllegalAccountStateException("an administrator cannot suspend themselves");
    }
    transaction.executeWithoutResult(
        status -> {
          Account account = accounts.findById(target).orElseThrow(AccountNotFoundException::new);
          account.suspend();
          accounts.update(account);
          revoker.revokeAll(target, "account-suspended");
          events.suspended(target.value());
          trail.adminAction(
              actor,
              IdentityAuditAction.ACCOUNT_SUSPENDED,
              AuditActors.subject(account),
              Map.of("reasonCode", reasonCode));
        });
  }

  public void reinstate(CurrentPrincipal actor, AccountId target) {
    access.require(actor, AdminPermission.ACCOUNT_REINSTATE);
    transaction.executeWithoutResult(
        status -> {
          Account account = accounts.findById(target).orElseThrow(AccountNotFoundException::new);
          account.reinstate();
          accounts.update(account);
          events.reinstated(target.value());
          trail.adminAction(
              actor,
              IdentityAuditAction.ACCOUNT_REINSTATED,
              AuditActors.subject(account),
              Map.of());
        });
  }
}
