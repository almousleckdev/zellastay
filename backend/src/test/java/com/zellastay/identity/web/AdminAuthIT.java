package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.identity.domain.Base32;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionLifetime;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.identity.domain.Totp;
import com.zellastay.shared.security.TokenAudience;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class AdminAuthIT {

  @Value("${local.server.port}")
  int port;

  @Autowired AdminFixtures admins;
  @Autowired AccountFixtures fixtures;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionRepository sessions;
  @Autowired AccessTokens accessTokens;
  @Autowired Clock clock;

  ApiClient api;

  @BeforeEach
  void client() {
    api = new ApiClient(port);
  }

  private Response password(Admin admin) {
    return api.post("/api/v1/admin/auth/login", map("email", admin.email(), "password", PASSWORD));
  }

  private Response verify(String ticket, String code) {
    return api.post("/api/v1/admin/auth/mfa/verify", map("mfaTicket", ticket, "code", code));
  }

  private static String ticket(Response response) {
    return response.json().path("mfaTicket").asString();
  }

  @Test
  void thePasswordAloneNeverYieldsAnAccessToken() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);

    Response response = password(admin);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("mfaEnrolled").asBoolean()).isTrue();
    assertThat(ticket(response)).matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}");
    assertThat(response.body()).doesNotContain("accessToken").doesNotContain("refreshToken");
  }

  @Test
  void aValidSecondFactorCompletesLoginAndTheTokenWorksOnAdminRoutesOnly() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    String ticket = ticket(password(admin));

    Response done = verify(ticket, admins.currentCode(admin));

    assertThat(done.status()).isEqualTo(200);
    String access = done.json().path("accessToken").asString();
    assertThat(api.get("/api/v1/admin/me/sessions", "Authorization", bearer(access)).status())
        .isEqualTo(200);
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(access)).status())
        .isEqualTo(401);
    assertThat(api.get("/api/v1/vendor/me/sessions", "Authorization", bearer(access)).status())
        .isEqualTo(401);
  }

  @Test
  void aWrongCodeIsRejectedAndTheFifthKillsTheTicket() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    String ticket = ticket(password(admin));
    String good = admins.currentCode(admin);
    String wrong = good.equals("000000") ? "111111" : "000000";

    for (int i = 0; i < 4; i++) {
      Response response = verify(ticket, wrong);
      assertThat(response.status()).isEqualTo(401);
      assertThat(response.code()).isEqualTo("identity.mfa-invalid");
    }
    Response fifth = verify(ticket, wrong);
    Response afterBurn = verify(ticket, good);

    assertThat(fifth.code()).isEqualTo("identity.mfa-required");
    assertThat(afterBurn.code()).isEqualTo("identity.mfa-required");
    assertThat(afterBurn.status()).isEqualTo(401);
  }

  @Test
  void aCodeCannotBeReplayedForASecondLogin() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    String code = admins.currentCode(admin);
    String firstTicket = ticket(password(admin));
    String secondTicket = ticket(password(admin));

    Response first = verify(firstTicket, code);
    Response replay = verify(secondTicket, code);

    assertThat(first.status()).isEqualTo(200);
    assertThat(replay.status()).isEqualTo(401);
    assertThat(replay.code()).isEqualTo("identity.mfa-invalid");
  }

  @Test
  void aForgedOrExpiredTicketIsRejected() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    String ticket = ticket(password(admin));
    String id = ticket.substring(0, ticket.indexOf('.'));
    String code = admins.currentCode(admin);

    assertThat(verify(id + "." + "A".repeat(43), code).code()).isEqualTo("identity.mfa-required");
    assertThat(verify("garbage", code).code()).isEqualTo("identity.mfa-required");
    jdbc.update(
        "update ident_challenge set expires_at = now() - interval '1 second' where id = ?::uuid",
        id);
    assertThat(verify(ticket, code).code()).isEqualTo("identity.mfa-required");
  }

  @Test
  void firstTimeEnrolmentShowsTheSecretOnceAndHandsOutRecoveryCodes() {
    Admin admin = admins.createWithoutMfa(AdminRole.SUPER_ADMIN);
    Response login = password(admin);
    String ticket = ticket(login);
    assertThat(login.json().path("mfaEnrolled").asBoolean()).isFalse();
    assertThat(verify(ticket, "123456").code()).isEqualTo("identity.mfa-required");

    Response enrol = api.post("/api/v1/admin/auth/mfa/enroll", map("mfaTicket", ticket));

    assertThat(enrol.status()).isEqualTo(200);
    String secret = enrol.json().path("secret").asString();
    assertThat(enrol.json().path("otpauthUri").asString())
        .startsWith("otpauth://totp/Zella%20Stay:")
        .contains("secret=" + secret);
    assertThat(
            jdbc.queryForObject(
                "select secret_ciphertext from ident_admin_mfa where account_id = ?",
                String.class,
                admin.id().value()))
        .doesNotContain(secret);

    long step = java.time.Instant.now().getEpochSecond() / 30;
    Response done = verify(ticket, Totp.code(Base32.decode(secret), step));

    assertThat(done.status()).isEqualTo(200);
    JsonNode codes = done.json().path("recoveryCodes");
    assertThat(codes.size()).isEqualTo(10);
    assertThat(codes.get(0).asString()).matches("[A-Z2-7]{4}-[A-Z2-7]{4}-[A-Z2-7]{4}");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ident_recovery_code where account_id = ? and code_hash = ?",
                Integer.class,
                admin.id().value(),
                codes.get(0).asString()))
        .isZero();
    Response next = password(admin);
    assertThat(next.json().path("mfaEnrolled").asBoolean()).isTrue();
    assertThat(api.post("/api/v1/admin/auth/mfa/enroll", map("mfaTicket", ticket(next))).status())
        .isEqualTo(422);
  }

  @Test
  void aRecoveryCodeWorksExactlyOnce() {
    Admin admin = admins.createWithoutMfa(AdminRole.SUPER_ADMIN);
    String ticket = ticket(password(admin));
    String secret =
        api.post("/api/v1/admin/auth/mfa/enroll", map("mfaTicket", ticket))
            .json()
            .path("secret")
            .asString();
    Response enrolled =
        verify(
            ticket,
            Totp.code(Base32.decode(secret), java.time.Instant.now().getEpochSecond() / 30));
    String recovery = enrolled.json().path("recoveryCodes").get(0).asString();

    Response used =
        api.post(
            "/api/v1/admin/auth/mfa/verify",
            map("mfaTicket", ticket(password(admin)), "recoveryCode", recovery));
    Response reused =
        api.post(
            "/api/v1/admin/auth/mfa/verify",
            map("mfaTicket", ticket(password(admin)), "recoveryCode", recovery));

    assertThat(used.status()).isEqualTo(200);
    assertThat(reused.status()).isEqualTo(401);
    assertThat(reused.code()).isEqualTo("identity.mfa-invalid");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_event where action = 'admin.mfa.recovery-used' and actor_id = ?",
                Integer.class,
                admin.id().value()))
        .isEqualTo(1);
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
