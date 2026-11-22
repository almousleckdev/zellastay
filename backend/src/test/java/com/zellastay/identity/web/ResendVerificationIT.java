package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.uniqueEmail;
import static com.zellastay.support.ApiClient.map;
import static com.zellastay.support.RegistrationCalls.register;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import com.zellastay.support.TestMailbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Resending the verification code: cooldown, replacement and uniform answers. */
@IntegrationTest
class ResendVerificationIT {

  private static final String PASSWORD = "Tr0ub4dor&3-horse";

  @Autowired JdbcTemplate jdbc;
  @Autowired TestMailbox mailbox;

  ApiClient api;

  @Test
  void resendInsideTheCooldownSendsNothingNewAndStillAnswersTheSame() {
    String email = uniqueEmail();
    register(api, "/api/v1/auth/register", email, PASSWORD);

    Response resend = api.post("/api/v1/auth/verify/resend", map("email", email));

    assertThat(resend.status()).isEqualTo(202);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ident_challenge c join ident_account a on a.id = c.account_id"
                    + " where a.email = ?",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void resendAfterTheCooldownIssuesANewCodeAndRetiresTheOldOne() {
    String email = uniqueEmail();
    register(api, "/api/v1/auth/register", email, PASSWORD);
    String oldCode = mailbox.verificationCode(email);
    jdbc.update(
        "update ident_challenge set created_at = created_at - interval '5 minutes'"
            + " where account_id = (select id from ident_account where email = ?)",
        email);

    Response resend = api.post("/api/v1/auth/verify/resend", map("email", email));
    String newCode = mailbox.verificationCode(email);

    assertThat(resend.status()).isEqualTo(202);
    assertThat(newCode).isNotEqualTo(oldCode);
    if (!oldCode.equals(newCode)) {
      assertThat(api.post("/api/v1/auth/verify", map("email", email, "code", oldCode)).status())
          .isEqualTo(400);
    }
    assertThat(api.post("/api/v1/auth/verify", map("email", email, "code", newCode)).status())
        .isEqualTo(204);
  }

  @Test
  void resendForAnUnknownEmailAnswersLikeAKnownOne() {
    Response response = api.post("/api/v1/auth/verify/resend", map("email", uniqueEmail()));

    assertThat(response.status()).isEqualTo(202);
  }
}
