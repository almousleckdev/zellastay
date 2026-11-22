package com.zellastay.identity.web;

import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.Base32;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.identity.domain.Totp;
import com.zellastay.support.AdminAuthCalls;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.AuditEvents;
import com.zellastay.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Step two of admin sign-in: codes, tickets and recovery codes. */
@IntegrationTest
class AdminMfaVerificationIT {

  @Autowired AdminFixtures admins;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuditEvents audit;
  @Autowired SessionRepository sessions;

  ApiClient api;

  @Test
  void aValidSecondFactorCompletesLoginAndTheTokenWorksOnAdminRoutesOnly() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    String ticket = AdminAuthCalls.ticket(AdminAuthCalls.password(api, admin));

    Response done = AdminAuthCalls.verify(api, ticket, admins.currentCode(admin));

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
    String ticket = AdminAuthCalls.ticket(AdminAuthCalls.password(api, admin));
    String good = admins.currentCode(admin);
    String wrong = good.equals("000000") ? "111111" : "000000";

    for (int i = 0; i < 4; i++) {
      Response response = AdminAuthCalls.verify(api, ticket, wrong);
      assertThat(response.status()).isEqualTo(401);
      assertThat(response.code()).isEqualTo("identity.mfa-invalid");
    }
    Response fifth = AdminAuthCalls.verify(api, ticket, wrong);
    Response afterBurn = AdminAuthCalls.verify(api, ticket, good);

    assertThat(fifth.code()).isEqualTo("identity.mfa-required");
    assertThat(afterBurn.code()).isEqualTo("identity.mfa-required");
    assertThat(afterBurn.status()).isEqualTo(401);
  }

  @Test
  void aCodeCannotBeReplayedForASecondLogin() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    String code = admins.currentCode(admin);
    String firstTicket = AdminAuthCalls.ticket(AdminAuthCalls.password(api, admin));
    String secondTicket = AdminAuthCalls.ticket(AdminAuthCalls.password(api, admin));

    Response first = AdminAuthCalls.verify(api, firstTicket, code);
    Response replay = AdminAuthCalls.verify(api, secondTicket, code);

    assertThat(first.status()).isEqualTo(200);
    assertThat(replay.status()).isEqualTo(401);
    assertThat(replay.code()).isEqualTo("identity.mfa-invalid");
  }

  @Test
  void aForgedOrExpiredTicketIsRejected() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    String ticket = AdminAuthCalls.ticket(AdminAuthCalls.password(api, admin));
    String id = ticket.substring(0, ticket.indexOf('.'));
    String code = admins.currentCode(admin);

    assertThat(AdminAuthCalls.verify(api, id + "." + "A".repeat(43), code).code())
        .isEqualTo("identity.mfa-required");
    assertThat(AdminAuthCalls.verify(api, "garbage", code).code())
        .isEqualTo("identity.mfa-required");
    jdbc.update(
        "update ident_challenge set expires_at = now() - interval '1 second' where id = ?::uuid",
        id);
    assertThat(AdminAuthCalls.verify(api, ticket, code).code()).isEqualTo("identity.mfa-required");
  }

  @Test
  void aRecoveryCodeWorksExactlyOnce() {
    Admin admin = admins.createWithoutMfa(AdminRole.SUPER_ADMIN);
    String ticket = AdminAuthCalls.ticket(AdminAuthCalls.password(api, admin));
    String secret =
        api.post("/api/v1/admin/auth/mfa/enroll", map("mfaTicket", ticket))
            .json()
            .path("secret")
            .asString();
    Response enrolled =
        AdminAuthCalls.verify(
            api,
            ticket,
            Totp.code(Base32.decode(secret), java.time.Instant.now().getEpochSecond() / 30));
    String recovery = enrolled.json().path("recoveryCodes").get(0).asString();

    Response used =
        api.post(
            "/api/v1/admin/auth/mfa/verify",
            map(
                "mfaTicket",
                AdminAuthCalls.ticket(AdminAuthCalls.password(api, admin)),
                "recoveryCode",
                recovery));
    Response reused =
        api.post(
            "/api/v1/admin/auth/mfa/verify",
            map(
                "mfaTicket",
                AdminAuthCalls.ticket(AdminAuthCalls.password(api, admin)),
                "recoveryCode",
                recovery));

    assertThat(used.status()).isEqualTo(200);
    assertThat(reused.status()).isEqualTo(401);
    assertThat(reused.code()).isEqualTo("identity.mfa-invalid");
    assertThat(audit.byActor("admin.mfa.recovery-used", admin.id().value())).isEqualTo(1);
  }
}
