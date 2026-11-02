package com.zellastay.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.AccountSummary;
import com.zellastay.identity.api.IdentityApi;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class IdentityApiIT {

  @Autowired IdentityApi identity;
  @Autowired AccountFixtures fixtures;

  @Test
  void findsAnAccountAndExposesOnlyASafeSummary() {
    String email = AccountFixtures.uniqueEmail();
    var account = fixtures.active(AccountKind.VENDOR_USER, email);

    AccountSummary summary = identity.findAccount(account.id()).orElseThrow();

    assertThat(summary.id()).isEqualTo(account.id());
    assertThat(summary.kind()).isEqualTo(AccountKind.VENDOR_USER);
    assertThat(summary.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(summary.emailVerified()).isTrue();
    assertThat(summary.maskedEmail()).doesNotContain(email).contains("***@");
    assertThat(summary.toString()).doesNotContain(email);
  }

  @Test
  void anUnknownAccountIsEmpty() {
    assertThat(identity.findAccount(new AccountId(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void aVendorUserIsFoundByVerifiedEmailOnly() {
    String email = AccountFixtures.uniqueEmail();
    var verified = fixtures.active(AccountKind.VENDOR_USER, email);
    String pendingEmail = AccountFixtures.uniqueEmail();
    fixtures.pending(AccountKind.VENDOR_USER, pendingEmail);
    String customerOnly = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, customerOnly);

    assertThat(identity.findVendorUserByVerifiedEmail(email.toUpperCase()))
        .map(AccountSummary::id)
        .contains(verified.id());
    assertThat(identity.findVendorUserByVerifiedEmail(pendingEmail)).isEmpty();
    assertThat(identity.findVendorUserByVerifiedEmail(customerOnly)).isEmpty();
    assertThat(identity.findVendorUserByVerifiedEmail("not an email")).isEmpty();
  }
}
