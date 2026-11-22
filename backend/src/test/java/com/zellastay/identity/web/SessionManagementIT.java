package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import com.zellastay.support.SessionCalls;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Listing, revoking and ending one's own sessions. */
@IntegrationTest
class SessionManagementIT {

  private static final String NEW_PASSWORD = AccountFixtures.CHANGED_PASSWORD;

  @Autowired AccountFixtures fixtures;

  ApiClient api;

  @Test
  void sessionsAreListedWithTheCurrentOneMarked() {
    String email = fixtures.activeCustomerEmail();
    Auth.Tokens first = Auth.customer(api, email);
    Auth.Tokens second = Auth.customer(api, email);

    JsonNode list = SessionCalls.list(api, second).json();

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
    String email = fixtures.activeCustomerEmail();
    Auth.Tokens first = Auth.customer(api, email);
    Auth.Tokens second = Auth.customer(api, email);
    String firstId = idOfNonCurrent(SessionCalls.list(api, second).json());

    Response revoked =
        api.delete("/api/v1/me/sessions/" + firstId, "Authorization", bearer(second.access()));

    assertThat(revoked.status()).isEqualTo(204);
    assertThat(SessionCalls.list(api, first).status()).isEqualTo(401);
    assertThat(SessionCalls.list(api, second).status()).isEqualTo(200);
  }

  @Test
  void aSessionOfAnotherAccountCannotBeRevokedAndLooksNonexistent() {
    Auth.Tokens mine = Auth.customer(api, fixtures.activeCustomerEmail());
    Auth.Tokens theirs = Auth.customer(api, fixtures.activeCustomerEmail());
    String theirId = SessionCalls.list(api, theirs).json().get(0).path("id").asString();

    Response response =
        api.delete("/api/v1/me/sessions/" + theirId, "Authorization", bearer(mine.access()));

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.code()).isEqualTo("identity.session-not-found");
    assertThat(SessionCalls.list(api, theirs).status()).isEqualTo(200);
  }

  @Test
  void signingOutEverywhereRevokesEverySession() {
    String email = fixtures.activeCustomerEmail();
    Auth.Tokens first = Auth.customer(api, email);
    Auth.Tokens second = Auth.customer(api, email);

    Response response = api.delete("/api/v1/me/sessions", "Authorization", bearer(second.access()));

    assertThat(response.status()).isEqualTo(204);
    assertThat(SessionCalls.list(api, first).status()).isEqualTo(401);
    assertThat(SessionCalls.list(api, second).status()).isEqualTo(401);
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
