package com.zellastay.identity.web;

import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.Base32;
import com.zellastay.identity.domain.Totp;
import com.zellastay.support.AdminAuthCalls;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** First-time authenticator enrolment and recovery codes. */
@IntegrationTest
class AdminMfaEnrolmentIT {

  @Autowired AdminFixtures admins;
  @Autowired JdbcTemplate jdbc;

  ApiClient api;

  @Test
  void firstTimeEnrolmentShowsTheSecretOnceAndHandsOutRecoveryCodes() {
    Admin admin = admins.createWithoutMfa(AdminRole.SUPER_ADMIN);
    Response login = AdminAuthCalls.password(api, admin);
    String ticket = AdminAuthCalls.ticket(login);
    assertThat(login.json().path("mfaEnrolled").asBoolean()).isFalse();
    assertThat(AdminAuthCalls.verify(api, ticket, "123456").code())
        .isEqualTo("identity.mfa-required");

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
    Response done = AdminAuthCalls.verify(api, ticket, Totp.code(Base32.decode(secret), step));

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
    Response next = AdminAuthCalls.password(api, admin);
    assertThat(next.json().path("mfaEnrolled").asBoolean()).isTrue();
    assertThat(
            api.post("/api/v1/admin/auth/mfa/enroll", map("mfaTicket", AdminAuthCalls.ticket(next)))
                .status())
        .isEqualTo(422);
  }
}
