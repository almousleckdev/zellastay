package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.infrastructure.InMemoryVerificationDelivery;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.AuditEvents;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import com.zellastay.support.SessionCalls;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Changing one's own password. */
@IntegrationTest
class ChangePasswordIT {

  private static final String NEW_PASSWORD = AccountFixtures.CHANGED_PASSWORD;

  @Autowired AccountFixtures fixtures;
  @Autowired AuditEvents audit;
  @Autowired InMemoryVerificationDelivery mailbox;

  ApiClient api;

  @Test
  void changingThePasswordReplacesItAndSignsOutOtherSessionsOnly() {
    String email = fixtures.activeCustomerEmail();
    Auth.Tokens other = Auth.customer(api, email);
    Auth.Tokens current = Auth.customer(api, email);

    Response changed =
        api.post(
            "/api/v1/me/password",
            map("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD),
            "Authorization",
            bearer(current.access()));

    assertThat(changed.status()).isEqualTo(204);
    assertThat(SessionCalls.list(api, current).status()).isEqualTo(200);
    assertThat(SessionCalls.list(api, other).status()).isEqualTo(401);
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(401);
    assertThat(
            api.post("/api/v1/auth/login", map("email", email, "password", NEW_PASSWORD)).status())
        .isEqualTo(200);
    assertThat(mailbox.latest(email, DeliveryTemplate.PASSWORD_CHANGED)).isPresent();
    assertThat(audit.byActorEmail("account.password-changed", email)).isEqualTo(1);
  }

  @Test
  void aWrongCurrentPasswordOrAWeakNewOneChangesNothing() {
    String email = fixtures.activeCustomerEmail();
    Auth.Tokens token = Auth.customer(api, email);

    Response wrong =
        api.post(
            "/api/v1/me/password",
            map("currentPassword", "Wrong-passw0rd!!", "newPassword", NEW_PASSWORD),
            "Authorization",
            bearer(token.access()));
    Response weak =
        api.post(
            "/api/v1/me/password",
            map("currentPassword", PASSWORD, "newPassword", "password123"),
            "Authorization",
            bearer(token.access()));

    assertThat(wrong.status()).isEqualTo(400);
    assertThat(wrong.code()).isEqualTo("identity.current-password-invalid");
    assertThat(weak.code()).isEqualTo("identity.password-rejected");
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(200);
  }
}
