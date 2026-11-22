package com.zellastay.support;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.Profile;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Plain domain objects for repository tests, built without the application context. */
public final class RepositoryTestData {

  /** Truncated to the precision PostgreSQL stores, so round trips compare equal. */
  public static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

  private RepositoryTestData() {}

  public static Account newAccount(AccountKind kind, String email) {
    return Account.register(
        AccountId.random(), kind, Email.of(email), "hash", new Profile("Jane", "Doe"), "fr", NOW);
  }
}
