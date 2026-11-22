package com.zellastay.support;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.Profile;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates ready-made accounts for integration tests, bypassing the HTTP registration flow. */
@Component
public class AccountFixtures {

  public static final String PASSWORD = "Tr0ub4dor&3-horse";

  /** A second valid password, for tests that change or reset it. */
  public static final String CHANGED_PASSWORD = PASSWORD + "-changed";

  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final TransactionTemplate transaction;
  private final Clock clock;

  AccountFixtures(
      AccountRepository accounts,
      PasswordHasher hasher,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.transaction = transaction;
    this.clock = clock;
  }

  public static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }

  public String activeCustomerEmail() {
    String email = uniqueEmail();
    active(AccountKind.CUSTOMER, email);
    return email;
  }

  public Account active(AccountKind kind, String email) {
    Account account = pending(kind, email);
    account.verifyEmail(clock.instant());
    transaction.executeWithoutResult(s -> accounts.update(account));
    return accounts.findById(account.id()).orElseThrow();
  }

  public Account pending(AccountKind kind, String email) {
    Account account =
        Account.register(
            AccountId.random(),
            kind,
            Email.of(email),
            hasher.hash(PASSWORD),
            new Profile("Jane", "Doe"),
            "fr",
            clock.instant());
    transaction.executeWithoutResult(s -> accounts.insert(account));
    return accounts.findById(account.id()).orElseThrow();
  }
}
