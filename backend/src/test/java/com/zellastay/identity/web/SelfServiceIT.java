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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class SelfServiceIT {

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

  private String customerAccount() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    return email;
  }

  private Response sessions(Auth.Tokens token) {
    return api.get("/api/v1/me/sessions", "Authorization", bearer(token.access()));
  }

  @Test
  void sessionsAreListedWithTheCurrentOneMarked() {
    String email = customerAccount();
    Auth.Tokens first = Auth.customer(api, email);
    Auth.Tokens second = Auth.customer(api, email);

    JsonNode list = sessions(second).json();

    assertThat(list.size()).isEqualTo(2);
    long current = 0;
    for (JsonNode session : list) {
      current += session.path("current").asBoolean() ? 1 : 0;
      assertThat(session.path("id").asString()).isNotBlank();
      assertThat(session.has("refreshToken")).isFalse();
    }
    assertThat(current).isEqualTo(1);
    assertThat(first.access()).isNotEqualTo(second.access());
  }

  @Test
  void anotherSessionCanBeRevokedAndItsTokenStopsWorking() {
    String email = customerAccount();
    Auth.Tokens first = Auth.customer(api, email);
    Auth.Tokens second = Auth.customer(api, email);
    String firstId = idOfNonCurrent(sessions(second).json());

    Response revoked =
        api.delete("/api/v1/me/sessions/" + firstId, "Authorization", bearer(second.access()));

    assertThat(revoked.status()).isEqualTo(204);
    assertThat(sessions(first).status()).isEqualTo(401);
    assertThat(sessions(second).status()).isEqualTo(200);
  }

  @Test
  void aSessionOfAnotherAccountCannotBeRevokedAndLooksNonexistent() {
    Auth.Tokens mine = Auth.customer(api, customerAccount());
    Auth.Tokens theirs = Auth.customer(api, customerAccount());
    String theirId = sessions(theirs).json().get(0).path("id").asString();

    Response response =
        api.delete("/api/v1/me/sessions/" + theirId, "Authorization", bearer(mine.access()));

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.code()).isEqualTo("identity.session-not-found");
    assertThat(sessions(theirs).status()).isEqualTo(200);
  }

  @Test
  void signingOutEverywhereRevokesEverySession() {
    String email = customerAccount();
    Auth.Tokens first = Auth.customer(api, email);
    Auth.Tokens second = Auth.customer(api, email);

    Response response = api.delete("/api/v1/me/sessions", "Authorization", bearer(second.access()));

    assertThat(response.status()).isEqualTo(204);
    assertThat(sessions(first).status()).isEqualTo(401);
    assertThat(sessions(second).status()).isEqualTo(401);
  }

  @Test
  void changingThePasswordReplacesItAndSignsOutOtherSessionsOnly() {
    String email = customerAccount();
    Auth.Tokens other = Auth.customer(api, email);
    Auth.Tokens current = Auth.customer(api, email);

    Response changed =
        api.post(
            "/api/v1/me/password",
            map("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD),
            "Authorization",
            bearer(current.access()));

    assertThat(changed.status()).isEqualTo(204);
    assertThat(sessions(current).status()).isEqualTo(200);
    assertThat(sessions(other).status()).isEqualTo(401);
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(401);
    assertThat(
            api.post("/api/v1/auth/login", map("email", email, "password", NEW_PASSWORD)).status())
        .isEqualTo(200);
    assertThat(mailbox.latest(email, DeliveryTemplate.PASSWORD_CHANGED)).isPresent();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_event where action = 'account.password-changed'"
                    + " and actor_id = (select id from ident_account where email = ?)",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void aWrongCurrentPasswordOrAWeakNewOneChangesNothing() {
    String email = customerAccount();
    Auth.Tokens token = Auth.customer(api, email);

    Response wrong =
        api.post(
            "/api/v1/me/password",
            map("currentPassword", "Wrong-passw0rd!!", "newPassword", NEW_PASSWORD),
            "Authorization",
            bearer(token.access()));
    Response weak =
        api.post(
            "/api/v1/me/password",
            map("currentPassword", PASSWORD, "newPassword", "password123"),
            "Authorization",
            bearer(token.access()));

    assertThat(wrong.status()).isEqualTo(400);
    assertThat(wrong.code()).isEqualTo("identity.current-password-invalid");
    assertThat(weak.code()).isEqualTo("identity.password-rejected");
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(200);
  }

  @Test
  void vendorUsersHaveTheSameSelfServiceUnderTheirOwnPrefix() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.VENDOR_USER, email);
    Auth.Tokens token = Auth.vendor(api, email);

    Response list = api.get("/api/v1/vendor/me/sessions", "Authorization", bearer(token.access()));
    Response change =
        api.post(
            "/api/v1/vendor/me/password",
            map("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD),
            "Authorization",
            bearer(token.access()));

    assertThat(list.status()).isEqualTo(200);
    assertThat(change.status()).isEqualTo(204);
  }

  private static String idOfNonCurrent(JsonNode list) {
    for (JsonNode session : list) {
      if (!session.path("current").asBoolean()) {
        return session.path("id").asString();
      }
    }
    throw new IllegalStateException("no non-current session");
  }
}
