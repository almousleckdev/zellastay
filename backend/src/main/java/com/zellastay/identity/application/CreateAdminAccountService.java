package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminAccessApi;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.Profile;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates an administrator who sets their own password through the emailed setup link. */
@Service
public class CreateAdminAccountService {

  private record Invitation(AccountId id, DeliveryRequest message) {}

  private final AdminAccessApi access;
  private final AccountRepository accounts;
  private final AdminRoleRepository roles;
  private final AdminInvitations invitations;
  private final QuietDelivery delivery;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;
  private final Clock clock;

  CreateAdminAccountService(
      AdminAccessApi access,
      AccountRepository accounts,
      AdminRoleRepository roles,
      AdminInvitations invitations,
      QuietDelivery delivery,
      IdentityAuditTrail trail,
      TransactionTemplate transaction,
      Clock clock) {
    this.access = access;
    this.accounts = accounts;
    this.roles = roles;
    this.invitations = invitations;
    this.delivery = delivery;
    this.trail = trail;
    this.transaction = transaction;
    this.clock = clock;
  }

  public AccountId createAdmin(
      CurrentPrincipal actor, String rawEmail, String firstName, String lastName, AdminRole role) {
    access.require(actor, AdminPermission.ADMIN_MANAGE);
    Email email = Email.of(rawEmail);
    String unusablePassword = invitations.unusablePasswordHash();
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
    DeliveryRequest message = invitations.invitationFor(account);
    trail.adminAction(
        actor,
        IdentityAuditAction.ADMIN_ACCOUNT_CREATED,
        AuditActors.subject(account),
        Map.of("role", role.name()));
    return new Invitation(account.id(), message);
  }
}
