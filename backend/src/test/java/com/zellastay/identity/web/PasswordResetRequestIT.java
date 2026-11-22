package com.zellastay.identity.web;

import static com.zellastay.support.ApiClient.map;
import static com.zellastay.support.PasswordRecoveryCalls.requestToken;
import static com.zellastay.support.PasswordRecoveryCalls.reset;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import com.zellastay.support.TestMailbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Requesting a password reset: uniform answers, token storage and cooldown. */
@IntegrationTest
class PasswordResetRequestIT {

  private static final String NEW_PASSWORD = AccountFixtures.CHANGED_PASSWORD;

  @Autowired AccountFixtures fixtures;
  @Autowired JdbcTemplate jdbc;
  @Autowired TestMailbox mailbox;

  ApiClient api;

  @Test
  void aKnownAndAnUnknownEmailGetTheSameResponseButOnlyTheKnownOneGetsAToken() {
    String email = fixtures.activeCustomerEmail();
    String unknown = AccountFixtures.uniqueEmail();

    Response known = api.post("/api/v1/auth/password/forgot", map("email", email));
    Response missing = api.post("/api/v1/auth/password/forgot", map("email", unknown));

    assertThat(known.status()).isEqualTo(202).isEqualTo(missing.status());
    assertThat(known.body()).isEqualTo(missing.body());
    assertThat(mailbox.latest(email, DeliveryTemplate.PASSWORD_RESET)).isPresent();
    assertThat(mailbox.latest(unknown, DeliveryTemplate.PASSWORD_RESET)).isEmpty();
  }

  @Test
  void theTokenIsStoredOnlyAsAHash() {
    String email = fixtures.activeCustomerEmail();
    String token = requestToken(api, mailbox, email);
    String secret = token.substring(token.indexOf('.') + 1);

    assertThat(token).matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ident_challenge where secret_hash = ? or secret_hash = ?",
                Integer.class,
                token,
                secret))
        .isZero();
  }

  @Test
  void suspendedAccountsGetNoTokenAndCannotReset() {
    String email = fixtures.activeCustomerEmail();
    String token = requestToken(api, mailbox, email);
    jdbc.update("update ident_account set status = 'SUSPENDED' where email = ?", email);

    assertThat(reset(api, token, NEW_PASSWORD).code()).isEqualTo("identity.reset-token-invalid");
    jdbc.update("update ident_challenge set created_at = created_at - interval '5 minutes'");
    api.post("/api/v1/auth/password/forgot", map("email", email));
    String latest = mailbox.resetToken(email);
    assertThat(latest).isEqualTo(token);
  }

  @Test
  void aSecondRequestInsideTheCooldownIssuesNothingNew() {
    String email = fixtures.activeCustomerEmail();
    requestToken(api, mailbox, email);

    api.post("/api/v1/auth/password/forgot", map("email", email));

    assertThat(
            jdbc.queryForObject(
                "select count(*) from ident_challenge c join ident_account a on a.id = c.account_id"
                    + " where a.email = ? and c.purpose = 'PASSWORD_RESET'",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void vendorUsersRecoverUnderTheirOwnPrefix() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.VENDOR_USER, email);

    assertThat(api.post("/api/v1/vendor/auth/password/forgot", map("email", email)).status())
        .isEqualTo(202);
    String token = mailbox.resetToken(email);
    Response done =
        api.post(
            "/api/v1/vendor/auth/password/reset", map("token", token, "newPassword", NEW_PASSWORD));

    assertThat(done.status()).isEqualTo(204);
    assertThat(
            api.post("/api/v1/vendor/auth/login", map("email", email, "password", NEW_PASSWORD))
                .status())
        .isEqualTo(200);
  }
}
