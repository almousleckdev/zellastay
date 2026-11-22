package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.uniqueEmail;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.AuditEvents;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Refresh-token rotation, reuse detection, session lifetimes and logout. */
@IntegrationTest
class RefreshSessionIT {

  @Autowired AccountFixtures fixtures;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuditEvents audit;

  ApiClient api;

  private Response refresh(String path, String token) {
    return Auth.refresh(api, path, token);
  }

  @Test
  void refreshRotatesTheTokenAndKeepsTheSession() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    String first = Auth.customer(api, email).refresh();

    Response rotated = refresh("/api/v1/auth/refresh", first);

    assertThat(rotated.status()).isEqualTo(200);
    String second = rotated.json().path("refreshToken").asString();
    assertThat(second).isNotEqualTo(first);
    assertThat(rotated.json().path("accessToken").asString()).isNotBlank();
    assertThat(refresh("/api/v1/auth/refresh", second).status()).isEqualTo(200);
  }

  @Test
  void aRetryJustAfterRotationIsToleratedButAStaleReuseRevokesTheWholeSession() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    String first = Auth.customer(api, email).refresh();
    refresh("/api/v1/auth/refresh", first);

    Response retry = refresh("/api/v1/auth/refresh", first);
    jdbc.update(
        "update ident_refresh_token set used_at = used_at - interval '5 minutes' where used_at is not null");
    Response stale = refresh("/api/v1/auth/refresh", first);

    assertThat(retry.status()).isEqualTo(200);
    assertThat(stale.status()).isEqualTo(401);
    assertThat(stale.code()).isEqualTo("identity.refresh-invalid");
    String newest = retry.json().path("refreshToken").asString();
    assertThat(refresh("/api/v1/auth/refresh", newest).status()).isEqualTo(401);
    assertThat(audit.byActorEmail("auth.refresh.reuse-detected", email)).isEqualTo(1);
  }

  @Test
  void garbageAndForeignAudienceRefreshTokensAreRejected() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    String token = Auth.customer(api, email).refresh();

    assertThat(refresh("/api/v1/auth/refresh", "garbage").code())
        .isEqualTo("identity.refresh-invalid");
    assertThat(refresh("/api/v1/vendor/auth/refresh", token).code())
        .isEqualTo("identity.refresh-invalid");
    assertThat(refresh("/api/v1/auth/refresh", token).status()).isEqualTo(200);
  }

  @Test
  void sessionsExpireWhenIdleOrAfterTheAbsoluteLifetime() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    String idle = Auth.customer(api, email).refresh();
    jdbc.update(
        "update ident_session set last_used_at = now() - interval '15 days'"
            + " where account_id = (select id from ident_account where email = ?)",
        email);
    String absolute = Auth.customer(api, email).refresh();
    jdbc.update(
        "update ident_session set absolute_expires_at = now() - interval '1 second'"
            + " where last_used_at > now() - interval '1 day'"
            + " and account_id = (select id from ident_account where email = ?)",
        email);

    assertThat(refresh("/api/v1/auth/refresh", idle).status()).isEqualTo(401);
    assertThat(refresh("/api/v1/auth/refresh", absolute).status()).isEqualTo(401);
  }

  @Test
  void logoutRevokesTheSessionAndIsIdempotent() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    String token = Auth.customer(api, email).refresh();

    Response first = api.post("/api/v1/auth/logout", map("refreshToken", token));
    Response second = api.post("/api/v1/auth/logout", map("refreshToken", token));
    Response unknown = api.post("/api/v1/auth/logout", map("refreshToken", "never-issued-token"));

    assertThat(first.status()).isEqualTo(204);
    assertThat(second.status()).isEqualTo(204);
    assertThat(unknown.status()).isEqualTo(204);
    assertThat(refresh("/api/v1/auth/refresh", token).status()).isEqualTo(401);
  }
}
