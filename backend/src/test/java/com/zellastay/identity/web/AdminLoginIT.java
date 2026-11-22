package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionLifetime;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.shared.security.TokenAudience;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.AdminAuthCalls;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Step one of admin sign-in and the sessions it produces. */
@IntegrationTest
class AdminLoginIT {

  @Autowired AdminFixtures admins;
  @Autowired AccountFixtures fixtures;
  @Autowired SessionRepository sessions;
  @Autowired AccessTokens accessTokens;
  @Autowired Clock clock;

  ApiClient api;

  @Test
  void thePasswordAloneNeverYieldsAnAccessToken() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);

    Response response = AdminAuthCalls.password(api, admin);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("mfaEnrolled").asBoolean()).isTrue();
    assertThat(AdminAuthCalls.ticket(response)).matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}");
    assertThat(response.body()).doesNotContain("accessToken").doesNotContain("refreshToken");
  }

  @Test
  void onlyAdminAccountsCanUseTheAdminLoginAndFailuresLookTheSame() {
    String customer = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, customer);

    Response asCustomer =
        api.post("/api/v1/admin/auth/login", map("email", customer, "password", PASSWORD));
    Response unknown =
        api.post(
            "/api/v1/admin/auth/login",
            map("email", AccountFixtures.uniqueEmail(), "password", PASSWORD));

    assertThat(asCustomer.status()).isEqualTo(401);
    assertThat(asCustomer.code()).isEqualTo("identity.invalid-credentials");
    assertThat(unknown.code()).isEqualTo("identity.invalid-credentials");
  }

  @Test
  void aSessionWithoutASecondFactorIsRefusedOnEveryAdminRoute() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    Session session =
        Session.start(
            UUID.randomUUID(),
            admin.id(),
            TokenAudience.ADMIN,
            false,
            clock.instant(),
            new SessionLifetime(Duration.ofHours(1), Duration.ofMinutes(30)));
    sessions.insert(session);
    String token =
        accessTokens
            .issue(admin.id(), TokenAudience.ADMIN, session.id(), false, clock.instant())
            .value();

    Response response = api.get("/api/v1/admin/me/sessions", "Authorization", bearer(token));

    assertThat(response.status()).isEqualTo(403);
    assertThat(response.code()).isEqualTo("auth.forbidden");
  }

  @Test
  void adminSessionsCanBeRefreshedAndKeepTheirSecondFactor() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    var tokens = admins.login(api, admin);

    Response refreshed =
        api.post("/api/v1/admin/auth/refresh", map("refreshToken", tokens.refresh()));

    assertThat(refreshed.status()).isEqualTo(200);
    String access = refreshed.json().path("accessToken").asString();
    assertThat(api.get("/api/v1/admin/me/sessions", "Authorization", bearer(access)).status())
        .isEqualTo(200);
    assertThat(new AccountId(admin.id().value())).isEqualTo(admin.id());
  }
}
