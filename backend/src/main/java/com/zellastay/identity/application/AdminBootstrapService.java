package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.PasswordPolicy;
import com.zellastay.identity.domain.Profile;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates the very first Super Admin, and only while no administrator exists at all. */
@Service
public class AdminBootstrapService {

  private final AccountRepository accounts;
  private final AdminRoleRepository roles;
  private final PasswordHasher hasher;
  private final PasswordPolicy policy;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;
  private final Clock clock;

  AdminBootstrapService(
      AccountRepository accounts,
      AdminRoleRepository roles,
      PasswordHasher hasher,
      PasswordPolicy policy,
      IdentityAuditTrail trail,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.roles = roles;
    this.hasher = hasher;
    this.policy = policy;
    this.trail = trail;
    this.transaction = transaction;
    this.clock = clock;
  }

  public AdminBootstrapResult bootstrap(String rawEmail, String password) {
    if (accounts.existsOfKind(AccountKind.ADMIN)) {
      return AdminBootstrapResult.SKIPPED_ADMIN_EXISTS;
    }
    Email email = Email.of(rawEmail);
    policy.check(password, email);
    String hash = hasher.hash(password);
    return transaction.execute(status -> create(email, hash));
  }

  private AdminBootstrapResult create(Email email, String hash) {
    if (accounts.existsOfKind(AccountKind.ADMIN)) {
      return AdminBootstrapResult.SKIPPED_ADMIN_EXISTS;
    }
    Instant now = clock.instant();
    Account account =
        Account.registerTrusted(
            AccountId.random(),
            AccountKind.ADMIN,
            email,
            hash,
            new Profile("Platform", "Administrator"),
            SupportedLocale.DEFAULT,
            now);
    accounts.insert(account);
    roles.grant(account.id(), AdminRole.SUPER_ADMIN, now);
    trail.systemAction(account, IdentityAuditAction.ADMIN_BOOTSTRAP_EXECUTED);
    return AdminBootstrapResult.CREATED;
  }
}
