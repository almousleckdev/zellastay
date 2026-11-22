package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.uniqueEmail;
import static com.zellastay.support.ApiClient.map;
import static com.zellastay.support.RegistrationCalls.register;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.support.AccountRecords;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import com.zellastay.support.TestMailbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Email verification by code: activation, wrong codes and unknown addresses. */
@IntegrationTest
class VerifyEmailIT {

  private static final String PASSWORD = "Tr0ub4dor&3-horse";

  @Autowired JdbcTemplate jdbc;
  @Autowired TestMailbox mailbox;
  @Autowired AccountRecords accounts;

  ApiClient api;

  @Test
  void theCorrectCodeActivatesTheAccountAndCannotBeReused() {
    String email = uniqueEmail();
    register(api, "/api/v1/auth/register", email, PASSWORD);
    String code = mailbox.verificationCode(email);

    Response verified = api.post("/api/v1/auth/verify", map("email", email, "code", code));
    Response again = api.post("/api/v1/auth/verify", map("email", email, "code", code));

    assertThat(verified.status()).isEqualTo(204);
    assertThat(accounts.statusOf(email, "CUSTOMER")).isEqualTo("ACTIVE");
    assertThat(again.status()).isEqualTo(400);
    assertThat(again.code()).isEqualTo("identity.verification-invalid");
  }

  @Test
  void wrongCodesAreRejectedAndTheFifthBurnsTheChallenge() {
    String email = uniqueEmail();
    register(api, "/api/v1/auth/register", email, PASSWORD);
    String code = mailbox.verificationCode(email);
    String wrong = code.equals("000000") ? "111111" : "000000";

    for (int i = 0; i < 4; i++) {
      Response response = api.post("/api/v1/auth/verify", map("email", email, "code", wrong));
      assertThat(response.status()).isEqualTo(400);
      assertThat(response.code()).isEqualTo("identity.verification-invalid");
    }
    Response fifth = api.post("/api/v1/auth/verify", map("email", email, "code", wrong));
    Response correctAfterBurn = api.post("/api/v1/auth/verify", map("email", email, "code", code));

    assertThat(fifth.status()).isEqualTo(429);
    assertThat(fifth.code()).isEqualTo("identity.verification-attempts-exceeded");
    assertThat(correctAfterBurn.status()).isEqualTo(429);
    assertThat(accounts.statusOf(email, "CUSTOMER")).isEqualTo("PENDING_VERIFICATION");
  }

  @Test
  void verifyingAnUnknownEmailLooksLikeAWrongCode() {
    Response response =
        api.post("/api/v1/auth/verify", map("email", uniqueEmail(), "code", "123456"));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.code()).isEqualTo("identity.verification-invalid");
  }
}
