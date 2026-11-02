package com.zellastay.identity.web;

import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.infrastructure.InMemoryVerificationDelivery;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RegistrationIT {

  private static final String PASSWORD = "Tr0ub4dor&3-horse";

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;
  @Autowired InMemoryVerificationDelivery mailbox;

  ApiClient api;

  @BeforeEach
  void client() {
    api = new ApiClient(port);
  }

  private static String email() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }

  private Response register(String path, String email, String password) {
    return api.post(
        path,
        map(
            "email",
            email,
            "password",
            password,
            "firstName",
            "Jane",
            "lastName",
            "Doe",
            "locale",
            "fr"));
  }

  private String codeFor(String email) {
    DeliveryRequest request =
        mailbox.latest(email, DeliveryTemplate.EMAIL_VERIFICATION).orElseThrow();
    return request.secret();
  }

  private String statusOf(String email, String kind) {
    return jdbc.queryForObject(
        "select status from ident_account where email = ? and kind = ?", String.class, email, kind);
  }

  @Test
  void registrationAcceptsAndSendsACodeThatIsNeverStoredOrLogged(CapturedOutput output) {
    String email = email();

    Response response = register("/api/v1/auth/register", email, PASSWORD);

    assertThat(response.status()).isEqualTo(202);
    assertThat(statusOf(email, "CUSTOMER")).isEqualTo("PENDING_VERIFICATION");
    String code = codeFor(email);
    assertThat(code).matches("\\d{6}");
    assertThat(
            jdbc.queryForObject(
                "select secret_hash from ident_challenge c join ident_account a on a.id = c.account_id"
                    + " where a.email = ?",
                String.class,
                email))
        .isNotEqualTo(code)
        .hasSize(64);
    assertThat(output.getAll()).doesNotContain(code).doesNotContain(PASSWORD);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_event where action = 'account.registered' and subject_id ="
                    + " (select id::text from ident_account where email = ?)",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void anExistingEmailGetsTheSameResponseAndNoSecondAccount() {
    String email = email();
    Response first = register("/api/v1/auth/register", email, PASSWORD);

    Response second = register("/api/v1/auth/register", email, PASSWORD);

    assertThat(second.status()).isEqualTo(first.status()).isEqualTo(202);
    assertThat(second.body()).isEqualTo(first.body());
    assertThat(second.header("Content-Type")).isEqualTo(first.header("Content-Type"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ident_account where email = ?", Integer.class, email))
        .isEqualTo(1);
    assertThat(mailbox.latest(email, DeliveryTemplate.ACCOUNT_ALREADY_EXISTS)).isPresent();
  }

  @Test
  void concurrentRegistrationsOfOneEmailAllSucceedAndCreateOneAccount() throws Exception {
    String email = email();
    var pool = Executors.newFixedThreadPool(5);
    try {
      List<Callable<Integer>> calls =
          java.util.stream.IntStream.range(0, 5)
              .<Callable<Integer>>mapToObj(
                  i -> () -> register("/api/v1/auth/register", email, PASSWORD).status())
              .toList();
      for (Future<Integer> status : pool.invokeAll(calls)) {
        assertThat(status.get()).isEqualTo(202);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            jdbc.queryForObject(
                "select count(*) from ident_account where email = ?", Integer.class, email))
        .isEqualTo(1);
  }

  @Test
  void theCorrectCodeActivatesTheAccountAndCannotBeReused() {
    String email = email();
    register("/api/v1/auth/register", email, PASSWORD);
    String code = codeFor(email);

    Response verified = api.post("/api/v1/auth/verify", map("email", email, "code", code));
    Response again = api.post("/api/v1/auth/verify", map("email", email, "code", code));

    assertThat(verified.status()).isEqualTo(204);
    assertThat(statusOf(email, "CUSTOMER")).isEqualTo("ACTIVE");
    assertThat(again.status()).isEqualTo(400);
    assertThat(again.code()).isEqualTo("identity.verification-invalid");
  }

  @Test
  void wrongCodesAreRejectedAndTheFifthBurnsTheChallenge() {
    String email = email();
    register("/api/v1/auth/register", email, PASSWORD);
    String code = codeFor(email);
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
    assertThat(statusOf(email, "CUSTOMER")).isEqualTo("PENDING_VERIFICATION");
  }

  @Test
  void verifyingAnUnknownEmailLooksLikeAWrongCode() {
    Response response = api.post("/api/v1/auth/verify", map("email", email(), "code", "123456"));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.code()).isEqualTo("identity.verification-invalid");
  }

  @Test
  void resendInsideTheCooldownSendsNothingNewAndStillAnswersTheSame() {
    String email = email();
    register("/api/v1/auth/register", email, PASSWORD);

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
    String email = email();
    register("/api/v1/auth/register", email, PASSWORD);
    String oldCode = codeFor(email);
    jdbc.update(
        "update ident_challenge set created_at = created_at - interval '5 minutes'"
            + " where account_id = (select id from ident_account where email = ?)",
        email);

    Response resend = api.post("/api/v1/auth/verify/resend", map("email", email));
    String newCode = codeFor(email);

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
    Response response = api.post("/api/v1/auth/verify/resend", map("email", email()));

    assertThat(response.status()).isEqualTo(202);
  }

  @Test
  void weakPasswordsAndBadEmailsAreRejectedWithoutCreatingAccounts() {
    String email = email();

    Response weak = register("/api/v1/auth/register", email, "password123");
    Response shortPassword = register("/api/v1/auth/register", email, "short");
    Response badEmail = register("/api/v1/auth/register", "not-an-email", PASSWORD);

    assertThat(weak.code()).isEqualTo("identity.password-rejected");
    assertThat(weak.status()).isEqualTo(400);
    assertThat(shortPassword.code()).isEqualTo("identity.password-rejected");
    assertThat(badEmail.code()).isEqualTo("identity.email-invalid");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ident_account where email = ?", Integer.class, email))
        .isZero();
    assertThat(weak.body()).doesNotContain("password123");
  }

  @Test
  void missingFieldsProduceAValidationProblemWithoutEchoingValues() {
    Response response = api.post("/api/v1/auth/register", map("email", "x@example.com"));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.code()).isEqualTo("validation.failed");
    assertThat(response.body()).contains("password").doesNotContain("x@example.com");
  }

  @Test
  void vendorRegistrationCreatesAVendorUserAndMayShareAnEmailWithACustomer() {
    String email = email();
    register("/api/v1/auth/register", email, PASSWORD);

    Response vendor = register("/api/v1/vendor/auth/register", email, PASSWORD);

    assertThat(vendor.status()).isEqualTo(202);
    assertThat(statusOf(email, "VENDOR_USER")).isEqualTo("PENDING_VERIFICATION");
    assertThat(statusOf(email, "CUSTOMER")).isEqualTo("PENDING_VERIFICATION");
    String code = codeFor(email);
    assertThat(api.post("/api/v1/vendor/auth/verify", map("email", email, "code", code)).status())
        .isEqualTo(204);
    assertThat(statusOf(email, "VENDOR_USER")).isEqualTo("ACTIVE");
    assertThat(statusOf(email, "CUSTOMER")).isEqualTo("PENDING_VERIFICATION");
  }
}
