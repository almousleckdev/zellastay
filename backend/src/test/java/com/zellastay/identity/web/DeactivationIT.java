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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DeactivationIT {

  @Autowired AccountFixtures fixtures;
  @Autowired JdbcTemplate jdbc;

  ApiClient api;

  @Test
  void closingTheAccountSignsEverythingOutAndBlocksLogin() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    Auth.Tokens session = Auth.customer(api, email);

    Response response =
        api.post(
            "/api/v1/me/deactivation",
            map("password", PASSWORD),
            "Authorization",
            bearer(session.access()));

    assertThat(response.status()).isEqualTo(204);
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(session.access())).status())
        .isEqualTo(401);
    Response login = api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD));
    assertThat(login.status()).isEqualTo(401);
    assertThat(login.code()).isEqualTo("identity.invalid-credentials");
    assertThat(
            jdbc.queryForObject(
                "select status from ident_account where email = ?", String.class, email))
        .isEqualTo("DEACTIVATED");
  }

  @Test
  void aWrongPasswordChangesNothing() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.VENDOR_USER, email);
    Auth.Tokens session = Auth.vendor(api, email);

    Response response =
        api.post(
            "/api/v1/vendor/me/deactivation",
            map("password", "Wrong-passw0rd!!"),
            "Authorization",
            bearer(session.access()));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.code()).isEqualTo("identity.current-password-invalid");
    assertThat(
            api.get("/api/v1/vendor/me/sessions", "Authorization", bearer(session.access()))
                .status())
        .isEqualTo(200);
  }
}
