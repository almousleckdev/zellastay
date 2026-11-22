package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountNotFoundException;
import com.zellastay.identity.api.AdminAccessApi;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Lets an administrator sign another account out everywhere. */
@Service
public class AccountSessionRevocationService {

  private static final String REASON = "revoked-by-admin";

  private final AdminAccessApi access;
  private final AccountRepository accounts;
  private final SessionRevoker revoker;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;

  AccountSessionRevocationService(
      AdminAccessApi access,
      AccountRepository accounts,
      SessionRevoker revoker,
      IdentityAuditTrail trail,
      TransactionTemplate transaction) {
    this.access = access;
    this.accounts = accounts;
    this.revoker = revoker;
    this.trail = trail;
    this.transaction = transaction;
  }

  public void revokeSessions(CurrentPrincipal actor, AccountId target) {
    access.require(actor, AdminPermission.SESSION_REVOKE);
    transaction.executeWithoutResult(
        status -> {
          Account account = accounts.findById(target).orElseThrow(AccountNotFoundException::new);
          revoker.revokeAll(target, REASON);
          trail.adminAction(
              actor,
              IdentityAuditAction.SESSION_REVOKED,
              AuditActors.subject(account),
              Map.of("reason", REASON));
        });
  }
}
