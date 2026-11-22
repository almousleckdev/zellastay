package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static com.zellastay.support.PasswordRecoveryCalls.requestToken;
import static com.zellastay.support.PasswordRecoveryCalls.reset;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.AccountRecords;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.AuditEvents;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import com.zellastay.support.TestMailbox;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Completing a password reset with a token. */
@IntegrationTest
class PasswordResetIT {

  private static final String NEW_PASSWORD = AccountFixtures.CHANGED_PASSWORD;

  @Autowired AccountFixtures fixtures;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuditEvents audit;
  @Autowired TestMailbox mailbox;
  @Autowired AccountRecords accounts;

  ApiClient api;

  @Test
  void resetSetsTheNewPasswordSignsEverythingOutAndWorksOnlyOnce() {
    String email = fixtures.activeCustomerEmail();
    Auth.Tokens session = Auth.customer(api, email);
    String token = requestToken(api, mailbox, email);

    Response done = reset(api, token, NEW_PASSWORD);
    Response again = reset(api, token, "Another-Passw0rd-x");

    assertThat(done.status()).isEqualTo(204);
    assertThat(again.status()).isEqualTo(400);
    assertThat(again.code()).isEqualTo("identity.reset-token-invalid");
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(session.access())).status())
        .isEqualTo(401);
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(401);
    assertThat(
            api.post("/api/v1/auth/login", map("email", email, "password", NEW_PASSWORD)).status())
        .isEqualTo(200);
    assertThat(mailbox.latest(email, DeliveryTemplate.PASSWORD_CHANGED)).isPresent();
    assertThat(audit.byActorEmail("account.password-reset-completed", email)).isEqualTo(1);
  }

  @Test
  void aWeakNewPasswordIsRejectedWithoutConsumingTheToken() {
    String email = fixtures.activeCustomerEmail();
    String token = requestToken(api, mailbox, email);

    Response weak = reset(api, token, "password123");

    assertThat(weak.status()).isEqualTo(400);
    assertThat(weak.code()).isEqualTo("identity.password-rejected");
    assertThat(reset(api, token, NEW_PASSWORD).status()).isEqualTo(204);
  }

  @Test
  void expiredMalformedAndForgedTokensAllLookTheSame() {
    String email = fixtures.activeCustomerEmail();
    String token = requestToken(api, mailbox, email);
    String id = token.substring(0, token.indexOf('.'));
    String forged = id + "." + "A".repeat(43);
    String unknownId = UUID.randomUUID() + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    jdbc.update(
        "update ident_challenge set expires_at = now() - interval '1 second' where id = ?::uuid",
        id);

    for (String candidate :
        new String[] {token, forged, unknownId, "garbage", "no-dot", "a.b.c", ""}) {
      Response response = reset(api, candidate.isEmpty() ? "x" : candidate, NEW_PASSWORD);
      assertThat(response.status()).as(candidate).isEqualTo(400);
      assertThat(response.code()).as(candidate).isEqualTo("identity.reset-token-invalid");
    }
  }

  @Test
  void guessingTheSecretBurnsTheChallenge() {
    String email = fixtures.activeCustomerEmail();
    String token = requestToken(api, mailbox, email);
    String id = token.substring(0, token.indexOf('.'));

    for (int i = 0; i < 5; i++) {
      reset(api, id + "." + "B".repeat(43), NEW_PASSWORD);
    }

    assertThat(reset(api, token, NEW_PASSWORD).code()).isEqualTo("identity.reset-token-invalid");
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(200);
  }

  @Test
  void anUnverifiedAccountThatResetsItsPasswordIsVerifiedByIt() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.pending(AccountKind.CUSTOMER, email);
    String token = requestToken(api, mailbox, email);

    assertThat(reset(api, token, NEW_PASSWORD).status()).isEqualTo(204);

    assertThat(accounts.statusOf(email, "CUSTOMER")).isEqualTo("ACTIVE");
  }
}
