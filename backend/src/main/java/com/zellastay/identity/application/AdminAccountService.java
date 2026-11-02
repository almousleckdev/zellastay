package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountNotFoundException;
import com.zellastay.identity.api.AdminAccessApi;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.api.IllegalAccountStateException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.Profile;
import com.zellastay.identity.domain.SecretGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Administrator actions on accounts. Each method authorizes first (deny by default), changes state
 * and writes a fail-closed audit entry in the same transaction.
 */
@Service
public class AdminAccountService {

  private record Invitation(AccountId id, DeliveryRequest message) {}

  private final AdminAccessApi access;
  private final AccountRepository accounts;
  private final AdminRoleRepository roles;
  private final PasswordHasher hasher;
  private final SecretGenerator generator;
  private final ChallengeIssuer challenges;
  private final SessionRevoker revoker;
  private final QuietDelivery delivery;
  private final IdentityEvents events;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  AdminAccountService(
      AdminAccessApi access,
      AccountRepository accounts,
      AdminRoleRepository roles,
      PasswordHasher hasher,
      SecretGenerator generator,
      ChallengeIssuer challenges,
      SessionRevoker revoker,
      QuietDelivery delivery,
      IdentityEvents events,
      AuditRecorder audit,
      TransactionTemplate transaction,
      Clock clock) {
    this.access = access;
    this.accounts = accounts;
    this.roles = roles;
    this.hasher = hasher;
    this.generator = generator;
    this.challenges = challenges;
    this.revoker = revoker;
    this.delivery = delivery;
    this.events = events;
    this.audit = audit;
    this.transaction = transaction;
    this.clock = clock;
  }

  /** Creates an admin who sets their own password through the emailed reset link. */
  public AccountId createAdmin(
      CurrentPrincipal actor, String rawEmail, String firstName, String lastName, AdminRole role) {
    access.require(actor, AdminPermission.ADMIN_MANAGE);
    Email email = Email.of(rawEmail);
    String unusablePassword = hasher.hash(generator.token());
    Invitation invitation =
        transaction.execute(
            status -> create(actor, email, firstName, lastName, role, unusablePassword));
    delivery.send(invitation.message());
    return invitation.id();
  }

  private Invitation create(
      CurrentPrincipal actor,
      Email email,
      String firstName,
      String lastName,
      AdminRole role,
      String unusablePassword) {
    Instant now = clock.instant();
    Account account =
        Account.registerTrusted(
            AccountId.random(),
            AccountKind.ADMIN,
            email,
            unusablePassword,
            new Profile(firstName.strip(), lastName.strip()),
            SupportedLocale.DEFAULT,
            now);
    accounts.insert(account);
    roles.grant(account.id(), role, now);
    String token = challenges.issueResetToken(account);
    audit.record(
        new AuditEntry(
            SessionManagementService.actor(actor),
            IdentityAuditAction.ADMIN_ACCOUNT_CREATED,
            AuditActors.subject(account),
            AuditOutcome.SUCCESS,
            Map.of("role", role.name())));
    return new Invitation(
        account.id(),
        new DeliveryRequest(
            DeliveryTemplate.ADMIN_INVITATION, email.value(), account.snapshot().locale(), token));
  }

  public void grantRole(CurrentPrincipal actor, AccountId target, AdminRole role) {
    access.require(actor, AdminPermission.ADMIN_MANAGE);
    transaction.executeWithoutResult(
        status -> {
          requireAdmin(target);
          if (roles.grant(target, role, clock.instant())) {
            auditRole(actor, target, IdentityAuditAction.ADMIN_ROLE_GRANTED, role);
          }
        });
  }

  public void revokeRole(CurrentPrincipal actor, AccountId target, AdminRole role) {
    access.require(actor, AdminPermission.ADMIN_MANAGE);
    transaction.executeWithoutResult(
        status -> {
          requireAdmin(target);
          if (role == AdminRole.SUPER_ADMIN
              && roles.rolesOf(target).contains(role)
              && roles.countHolders(role) <= 1) {
            throw new IllegalAccountStateException("the last Super Admin cannot be removed");
          }
          if (roles.revoke(target, role)) {
            auditRole(actor, target, IdentityAuditAction.ADMIN_ROLE_REVOKED, role);
          }
        });
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
          audit.record(
              new AuditEntry(
                  SessionManagementService.actor(actor),
                  IdentityAuditAction.ACCOUNT_SUSPENDED,
                  AuditActors.subject(account),
                  AuditOutcome.SUCCESS,
                  Map.of("reasonCode", reasonCode)));
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
          audit.record(
              new AuditEntry(
                  SessionManagementService.actor(actor),
                  IdentityAuditAction.ACCOUNT_REINSTATED,
                  AuditActors.subject(account),
                  AuditOutcome.SUCCESS,
                  Map.of()));
        });
  }

  public void revokeSessions(CurrentPrincipal actor, AccountId target) {
    access.require(actor, AdminPermission.SESSION_REVOKE);
    transaction.executeWithoutResult(
        status -> {
          Account account = accounts.findById(target).orElseThrow(AccountNotFoundException::new);
          revoker.revokeAll(target, "revoked-by-admin");
          audit.record(
              new AuditEntry(
                  SessionManagementService.actor(actor),
                  IdentityAuditAction.SESSION_REVOKED,
                  AuditActors.subject(account),
                  AuditOutcome.SUCCESS,
                  Map.of("reason", "revoked-by-admin")));
        });
  }

  private void requireAdmin(AccountId target) {
    Account account = accounts.findById(target).orElseThrow(AccountNotFoundException::new);
    if (account.snapshot().kind() != AccountKind.ADMIN) {
      throw new AccountNotFoundException();
    }
  }

  private void auditRole(
      CurrentPrincipal actor, AccountId target, IdentityAuditAction action, AdminRole role) {
    audit.record(
        new AuditEntry(
            SessionManagementService.actor(actor),
            action,
            new com.zellastay.audit.api.AuditSubject("account", target.value().toString()),
            AuditOutcome.SUCCESS,
            Map.of("role", role.name())));
  }
}
