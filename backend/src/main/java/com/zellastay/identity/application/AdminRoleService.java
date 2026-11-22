package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountNotFoundException;
import com.zellastay.identity.api.AdminAccessApi;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.api.IllegalAccountStateException;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.AdminRoleRepository;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Grants and revokes admin roles; the last Super Admin can never be removed. */
@Service
public class AdminRoleService {

  private final AdminAccessApi access;
  private final AccountRepository accounts;
  private final AdminRoleRepository roles;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;
  private final Clock clock;

  AdminRoleService(
      AdminAccessApi access,
      AccountRepository accounts,
      AdminRoleRepository roles,
      IdentityAuditTrail trail,
      TransactionTemplate transaction,
      Clock clock) {
    this.access = access;
    this.accounts = accounts;
    this.roles = roles;
    this.trail = trail;
    this.transaction = transaction;
    this.clock = clock;
  }

  public void grantRole(CurrentPrincipal actor, AccountId target, AdminRole role) {
    access.require(actor, AdminPermission.ADMIN_MANAGE);
    transaction.executeWithoutResult(
        status -> {
          requireAdmin(target);
          if (roles.grant(target, role, clock.instant())) {
            audit(actor, target, IdentityAuditAction.ADMIN_ROLE_GRANTED, role);
          }
        });
  }

  public void revokeRole(CurrentPrincipal actor, AccountId target, AdminRole role) {
    access.require(actor, AdminPermission.ADMIN_MANAGE);
    transaction.executeWithoutResult(
        status -> {
          requireAdmin(target);
          if (isLastSuperAdmin(target, role)) {
            throw new IllegalAccountStateException("the last Super Admin cannot be removed");
          }
          if (roles.revoke(target, role)) {
            audit(actor, target, IdentityAuditAction.ADMIN_ROLE_REVOKED, role);
          }
        });
  }

  private boolean isLastSuperAdmin(AccountId target, AdminRole role) {
    return role == AdminRole.SUPER_ADMIN
        && roles.rolesOf(target).contains(role)
        && roles.countHolders(role) <= 1;
  }

  private void requireAdmin(AccountId target) {
    boolean isAdmin =
        accounts
            .findById(target)
            .map(account -> account.snapshot().kind() == AccountKind.ADMIN)
            .orElseThrow(AccountNotFoundException::new);
    if (!isAdmin) {
      throw new AccountNotFoundException();
    }
  }

  private void audit(
      CurrentPrincipal actor, AccountId target, IdentityAuditAction action, AdminRole role) {
    trail.adminAction(actor, action, AuditActors.subject(target), Map.of("role", role.name()));
  }
}
