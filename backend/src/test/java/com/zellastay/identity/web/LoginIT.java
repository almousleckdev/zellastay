package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.AccountFixtures.uniqueEmail;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.infrastructure.Argon2PasswordHasher;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LoginIT {

  @Value("${local.server.port}")
  int port;

  @Autowired AccountFixtures fixtures;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean PasswordHasher hasher;

  ApiClient api;

  @BeforeEach
  void client() {
    api = new ApiClient(port);
  }

  private Response login(String path, String email, String password) {
    return api.post(path, map("email", email, "password", password));
  }

  private Response refresh(String path, String token) {
    return api.post(path, map("refreshToken", token));
  }

  private String payload(String jwt) {
    return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
  }

  @Test
  void aVerifiedCustomerReceivesAnAudienceBoundTokenPairAndNoSecretIsLogged(CapturedOutput output) {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);

    Response response = login("/api/v1/auth/login", email, PASSWORD);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("tokenType").asString()).isEqualTo("Bearer");
    assertThat(response.json().path("expiresIn").asInt()).isEqualTo(900);
    String access = response.json().path("accessToken").asString();
    String refresh = response.json().path("refreshToken").asString();
    assertThat(payload(access)).contains("\"aud\":\"zs-customer\"").contains("\"sid\"");
    assertThat(refresh).matches("[A-Za-z0-9_-]{43}");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ident_refresh_token where token_hash = ?",
                Integer.class,
                refresh))
        .isZero();
    assertThat(output.getAll())
        .doesNotContain(access)
        .doesNotContain(refresh)
        .doesNotContain(PASSWORD);
  }

  @Test
  void everyKindOfFailureLooksTheSame() {
    String known = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, known);
    String unverified = uniqueEmail();
    fixtures.pending(AccountKind.CUSTOMER, unverified);

    Response wrongPassword = login("/api/v1/auth/login", known, "Wrong-passw0rd!!");
    Response unknown = login("/api/v1/auth/login", uniqueEmail(), PASSWORD);
    Response notVerified = login("/api/v1/auth/login", unverified, PASSWORD);
    Response badEmail = login("/api/v1/auth/login", "nonsense", PASSWORD);
    Response otherAudience = login("/api/v1/vendor/auth/login", known, PASSWORD);

    for (Response response :
        new Response[] {wrongPassword, unknown, notVerified, badEmail, otherAudience}) {
      assertThat(response.status()).isEqualTo(401);
      assertThat(response.code()).isEqualTo("identity.invalid-credentials");
      assertThat(response.json().path("detail").asString())
          .isEqualTo(wrongPassword.json().path("detail").asString());
    }
  }

  @Test
  void unknownAccountsStillSpendThePasswordHashingTime() {
    login("/api/v1/auth/login", uniqueEmail(), PASSWORD);

    verify(hasher, atLeastOnce()).burn(any());
  }

  @Test
  void repeatedFailuresLockTheAccountEvenAgainstTheCorrectPassword() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    for (int i = 0; i < 5; i++) {
      login("/api/v1/auth/login", email, "Wrong-passw0rd!!");
    }

    Response locked = login("/api/v1/auth/login", email, PASSWORD);

    assertThat(locked.status()).isEqualTo(401);
    assertThat(locked.code()).isEqualTo("identity.invalid-credentials");
    assertThat(
            jdbc.queryForObject(
                "select locked_until is not null from ident_account where email = ?",
                Boolean.class,
                email))
        .isTrue();
    jdbc.update(
        "update ident_account set locked_until = now() - interval '1 second' where email = ?",
        email);
    assertThat(login("/api/v1/auth/login", email, PASSWORD).status()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select failed_login_count from ident_account where email = ?",
                Integer.class,
                email))
        .isZero();
  }

  @Test
  void aSuspendedAccountIsToldOnlyAfterTheCorrectPassword() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    jdbc.update("update ident_account set status = 'SUSPENDED' where email = ?", email);

    Response correct = login("/api/v1/auth/login", email, PASSWORD);
    Response wrong = login("/api/v1/auth/login", email, "Wrong-passw0rd!!");

    assertThat(correct.status()).isEqualTo(403);
    assertThat(correct.code()).isEqualTo("identity.account-suspended");
    assertThat(wrong.status()).isEqualTo(401);
    assertThat(wrong.code()).isEqualTo("identity.invalid-credentials");
  }

  @Test
  void weakerStoredPasswordHashesAreUpgradedOnLogin() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    String weak = new Argon2PasswordHasher(1024, 1, 1).hash(PASSWORD);
    jdbc.update("update ident_account set password_hash = ? where email = ?", weak, email);

    assertThat(login("/api/v1/auth/login", email, PASSWORD).status()).isEqualTo(200);

    String stored =
        jdbc.queryForObject(
            "select password_hash from ident_account where email = ?", String.class, email);
    assertThat(stored).isNotEqualTo(weak);
    assertThat(hasher.matches(PASSWORD, stored)).isTrue();
  }

  @Test
  void refreshRotatesTheTokenAndKeepsTheSession() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    String first =
        login("/api/v1/auth/login", email, PASSWORD).json().path("refreshToken").asString();

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
    String first =
        login("/api/v1/auth/login", email, PASSWORD).json().path("refreshToken").asString();
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
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_event where action = 'auth.refresh.reuse-detected'"
                    + " and actor_id = (select id from ident_account where email = ?)",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void garbageAndForeignAudienceRefreshTokensAreRejected() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    String token =
        login("/api/v1/auth/login", email, PASSWORD).json().path("refreshToken").asString();

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
    String idle =
        login("/api/v1/auth/login", email, PASSWORD).json().path("refreshToken").asString();
    jdbc.update(
        "update ident_session set last_used_at = now() - interval '15 days'"
            + " where account_id = (select id from ident_account where email = ?)",
        email);
    String absolute =
        login("/api/v1/auth/login", email, PASSWORD).json().path("refreshToken").asString();
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
    String token =
        login("/api/v1/auth/login", email, PASSWORD).json().path("refreshToken").asString();

    Response first = api.post("/api/v1/auth/logout", map("refreshToken", token));
    Response second = api.post("/api/v1/auth/logout", map("refreshToken", token));
    Response unknown = api.post("/api/v1/auth/logout", map("refreshToken", "never-issued-token"));

    assertThat(first.status()).isEqualTo(204);
    assertThat(second.status()).isEqualTo(204);
    assertThat(unknown.status()).isEqualTo(204);
    assertThat(refresh("/api/v1/auth/refresh", token).status()).isEqualTo(401);
  }

  @Test
  void loginsAreAudited() {
    String email = uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    login("/api/v1/auth/login", email, "Wrong-passw0rd!!");
    login("/api/v1/auth/login", email, PASSWORD);

    Integer failures =
        jdbc.queryForObject(
            "select count(*) from audit_event where action = 'auth.login.failure'"
                + " and actor_id = (select id from ident_account where email = ?)",
            Integer.class,
            email);
    Integer successes =
        jdbc.queryForObject(
            "select count(*) from audit_event where action = 'auth.login.success'"
                + " and actor_id = (select id from ident_account where email = ?)",
            Integer.class,
            email);
    assertThat(failures).isEqualTo(1);
    assertThat(successes).isEqualTo(1);
  }
}
