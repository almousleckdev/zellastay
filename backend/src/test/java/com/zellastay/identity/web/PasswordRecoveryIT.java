package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.infrastructure.InMemoryVerificationDelivery;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PasswordRecoveryIT {

  private static final String NEW_PASSWORD = AccountFixtures.CHANGED_PASSWORD;

  @Value("${local.server.port}")
  int port;

  @Autowired AccountFixtures fixtures;
  @Autowired JdbcTemplate jdbc;
  @Autowired InMemoryVerificationDelivery mailbox;

  ApiClient api;

  @BeforeEach
  void client() {
    api = new ApiClient(port);
  }

  private String activeCustomer() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    return email;
  }

  private String requestToken(String email) {
    Response response = api.post("/api/v1/auth/password/forgot", map("email", email));
    assertThat(response.status()).isEqualTo(202);
    return mailbox.latest(email, DeliveryTemplate.PASSWORD_RESET).orElseThrow().secret();
  }

  private Response reset(String token, String password) {
    return api.post("/api/v1/auth/password/reset", map("token", token, "newPassword", password));
  }

  private String statusOf(String email) {
    return jdbc.queryForObject(
        "select status from ident_account where email = ? and kind = 'CUSTOMER'",
        String.class,
        email);
  }

  @Test
  void aKnownAndAnUnknownEmailGetTheSameResponseButOnlyTheKnownOneGetsAToken() {
    String email = activeCustomer();
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
    String email = activeCustomer();
    String token = requestToken(email);
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
  void resetSetsTheNewPasswordSignsEverythingOutAndWorksOnlyOnce() {
    String email = activeCustomer();
    Auth.Tokens session = Auth.customer(api, email);
    String token = requestToken(email);

    Response done = reset(token, NEW_PASSWORD);
    Response again = reset(token, "Another-Passw0rd-x");

    assertThat(done.status()).isEqualTo(204);
    assertThat(again.status()).isEqualTo(400);
    assertThat(again.code()).isEqualTo("identity.reset-token-invalid");
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(session.access())).status())
        .isEqualTo(401);
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(401);
    assertThat(
            api.post("/api/v1/auth/login", map("email", email, "password", NEW_PASSWORD)).status())
        .isEqualTo(200);
    assertThat(mailbox.latest(email, DeliveryTemplate.PASSWORD_CHANGED)).isPresent();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_event where action = 'account.password-reset-completed'"
                    + " and actor_id = (select id from ident_account where email = ?)",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void aWeakNewPasswordIsRejectedWithoutConsumingTheToken() {
    String email = activeCustomer();
    String token = requestToken(email);

    Response weak = reset(token, "password123");

    assertThat(weak.status()).isEqualTo(400);
    assertThat(weak.code()).isEqualTo("identity.password-rejected");
    assertThat(reset(token, NEW_PASSWORD).status()).isEqualTo(204);
  }

  @Test
  void expiredMalformedAndForgedTokensAllLookTheSame() {
    String email = activeCustomer();
    String token = requestToken(email);
    String id = token.substring(0, token.indexOf('.'));
    String forged = id + "." + "A".repeat(43);
    String unknownId = UUID.randomUUID() + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    jdbc.update(
        "update ident_challenge set expires_at = now() - interval '1 second' where id = ?::uuid",
        id);

    for (String candidate :
        new String[] {token, forged, unknownId, "garbage", "no-dot", "a.b.c", ""}) {
      Response response = reset(candidate.isEmpty() ? "x" : candidate, NEW_PASSWORD);
      assertThat(response.status()).as(candidate).isEqualTo(400);
      assertThat(response.code()).as(candidate).isEqualTo("identity.reset-token-invalid");
    }
  }

  @Test
  void guessingTheSecretBurnsTheChallenge() {
    String email = activeCustomer();
    String token = requestToken(email);
    String id = token.substring(0, token.indexOf('.'));

    for (int i = 0; i < 5; i++) {
      reset(id + "." + "B".repeat(43), NEW_PASSWORD);
    }

    assertThat(reset(token, NEW_PASSWORD).code()).isEqualTo("identity.reset-token-invalid");
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(200);
  }

  @Test
  void anUnverifiedAccountThatResetsItsPasswordIsVerifiedByIt() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.pending(AccountKind.CUSTOMER, email);
    String token = requestToken(email);

    assertThat(reset(token, NEW_PASSWORD).status()).isEqualTo(204);

    assertThat(statusOf(email)).isEqualTo("ACTIVE");
  }

  @Test
  void suspendedAccountsGetNoTokenAndCannotReset() {
    String email = activeCustomer();
    String token = requestToken(email);
    jdbc.update("update ident_account set status = 'SUSPENDED' where email = ?", email);

    assertThat(reset(token, NEW_PASSWORD).code()).isEqualTo("identity.reset-token-invalid");
    jdbc.update("update ident_challenge set created_at = created_at - interval '5 minutes'");
    api.post("/api/v1/auth/password/forgot", map("email", email));
    String latest = mailbox.latest(email, DeliveryTemplate.PASSWORD_RESET).orElseThrow().secret();
    assertThat(latest).isEqualTo(token);
  }

  @Test
  void aSecondRequestInsideTheCooldownIssuesNothingNew() {
    String email = activeCustomer();
    requestToken(email);

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
    String token = mailbox.latest(email, DeliveryTemplate.PASSWORD_RESET).orElseThrow().secret();
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
