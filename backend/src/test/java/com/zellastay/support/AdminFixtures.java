package com.zellastay.support;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.map;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.identity.domain.Base32;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.MfaRepository;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.Profile;
import com.zellastay.identity.domain.SecretCipher;
import com.zellastay.identity.domain.Totp;
import com.zellastay.support.ApiClient.Response;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Ready-made administrators for integration tests. */
@Component
public class AdminFixtures {

  public record Admin(Account account, String email, byte[] secret) {

    public AccountId id() {
      return account.id();
    }
  }

  private final AccountRepository accounts;
  private final AdminRoleRepository roles;
  private final MfaRepository mfa;
  private final SecretCipher cipher;
  private final PasswordHasher hasher;
  private final TransactionTemplate transaction;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  AdminFixtures(
      AccountRepository accounts,
      AdminRoleRepository roles,
      MfaRepository mfa,
      SecretCipher cipher,
      PasswordHasher hasher,
      TransactionTemplate transaction,
      JdbcTemplate jdbc,
      Clock clock) {
    this.accounts = accounts;
    this.roles = roles;
    this.mfa = mfa;
    this.cipher = cipher;
    this.hasher = hasher;
    this.transaction = transaction;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** An active admin with the roles and a confirmed authenticator. */
  public Admin create(AdminRole... adminRoles) {
    Admin admin = createWithoutMfa(adminRoles);
    transaction.executeWithoutResult(
        s -> {
          mfa.savePending(admin.id(), cipher.encrypt(Base32.encode(admin.secret())));
          mfa.confirm(admin.id(), 1, clock.instant());
        });
    return admin;
  }

  public Admin createWithoutMfa(AdminRole... adminRoles) {
    String email = AccountFixtures.uniqueEmail();
    Account account =
        Account.registerTrusted(
            AccountId.random(),
            AccountKind.ADMIN,
            Email.of(email),
            hasher.hash(PASSWORD),
            new Profile("Ada", "Admin"),
            "fr",
            clock.instant());
    transaction.executeWithoutResult(
        s -> {
          accounts.insert(account);
          for (AdminRole role : adminRoles) {
            roles.grant(account.id(), role, clock.instant());
          }
        });
    return new Admin(account, email, Totp.newSecret());
  }

  /** The code for the current step; clears the replay guard so tests can sign in repeatedly. */
  public String currentCode(Admin admin) {
    jdbc.update(
        "update ident_admin_mfa set last_used_step = 1 where account_id = ?", admin.id().value());
    return Totp.code(admin.secret(), clock.instant().getEpochSecond() / 30);
  }

  /** Signs in through the real two-step flow and returns the tokens. */
  public Auth.Tokens loginAsSuperAdmin(ApiClient api) {
    return login(api, create(AdminRole.SUPER_ADMIN));
  }

  public Auth.Tokens login(ApiClient api, Admin admin) {
    Response first =
        api.post("/api/v1/admin/auth/login", map("email", admin.email(), "password", PASSWORD));
    if (first.status() != 200) {
      throw new IllegalStateException("admin login failed: " + first.body());
    }
    Response second =
        api.post(
            "/api/v1/admin/auth/mfa/verify",
            map(
                "mfaTicket",
                first.json().path("mfaTicket").asString(),
                "code",
                currentCode(admin)));
    if (second.status() != 200) {
      throw new IllegalStateException("admin mfa failed: " + second.body());
    }
    return new Auth.Tokens(
        second.json().path("accessToken").asString(),
        second.json().path("refreshToken").asString());
  }
}
