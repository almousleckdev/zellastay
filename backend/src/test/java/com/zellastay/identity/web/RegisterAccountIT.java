package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.uniqueEmail;
import static com.zellastay.support.ApiClient.map;
import static com.zellastay.support.RegistrationCalls.register;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.support.AccountRecords;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.AuditEvents;
import com.zellastay.support.IntegrationTest;
import com.zellastay.support.TestMailbox;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/** Account registration: acceptance, duplicates, validation and vendor accounts. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RegisterAccountIT {

  private static final String PASSWORD = "Tr0ub4dor&3-horse";

  @Autowired JdbcTemplate jdbc;
  @Autowired AuditEvents audit;
  @Autowired TestMailbox mailbox;
  @Autowired AccountRecords accounts;

  ApiClient api;

  @Test
  void registrationAcceptsAndSendsACodeThatIsNeverStoredOrLogged(CapturedOutput output) {
    String email = uniqueEmail();

    Response response = register(api, "/api/v1/auth/register", email, PASSWORD);

    assertThat(response.status()).isEqualTo(202);
    assertThat(accounts.statusOf(email, "CUSTOMER")).isEqualTo("PENDING_VERIFICATION");
    String code = mailbox.verificationCode(email);
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
    assertThat(audit.bySubjectEmail("account.registered", email)).isEqualTo(1);
  }

  @Test
  void anExistingEmailGetsTheSameResponseAndNoSecondAccount() {
    String email = uniqueEmail();
    Response first = register(api, "/api/v1/auth/register", email, PASSWORD);

    Response second = register(api, "/api/v1/auth/register", email, PASSWORD);

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
    String email = uniqueEmail();
    var pool = Executors.newFixedThreadPool(5);
    try {
      List<Callable<Integer>> calls =
          java.util.stream.IntStream.range(0, 5)
              .<Callable<Integer>>mapToObj(
                  i -> () -> register(api, "/api/v1/auth/register", email, PASSWORD).status())
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
  void weakPasswordsAndBadEmailsAreRejectedWithoutCreatingAccounts() {
    String email = uniqueEmail();

    Response weak = register(api, "/api/v1/auth/register", email, "password123");
    Response shortPassword = register(api, "/api/v1/auth/register", email, "short");
    Response badEmail = register(api, "/api/v1/auth/register", "not-an-email", PASSWORD);

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
    String email = uniqueEmail();
    register(api, "/api/v1/auth/register", email, PASSWORD);

    Response vendor = register(api, "/api/v1/vendor/auth/register", email, PASSWORD);

    assertThat(vendor.status()).isEqualTo(202);
    assertThat(accounts.statusOf(email, "VENDOR_USER")).isEqualTo("PENDING_VERIFICATION");
    assertThat(accounts.statusOf(email, "CUSTOMER")).isEqualTo("PENDING_VERIFICATION");
    String code = mailbox.verificationCode(email);
    assertThat(api.post("/api/v1/vendor/auth/verify", map("email", email, "code", code)).status())
        .isEqualTo(204);
    assertThat(accounts.statusOf(email, "VENDOR_USER")).isEqualTo("ACTIVE");
    assertThat(accounts.statusOf(email, "CUSTOMER")).isEqualTo("PENDING_VERIFICATION");
  }
}
