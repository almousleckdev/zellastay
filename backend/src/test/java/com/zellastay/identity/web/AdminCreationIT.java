package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.identity.infrastructure.InMemoryVerificationDelivery;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.AuditEvents;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Creating an administrator by invitation. */
@IntegrationTest
class AdminCreationIT {

  @Autowired AdminFixtures admins;
  @Autowired AdminRoleRepository roles;
  @Autowired AuditEvents audit;
  @Autowired InMemoryVerificationDelivery mailbox;

  ApiClient api;

  @Test
  void creatingAnAdminInvitesThemToSetAPasswordAndTheyThenSignInWithTheirSecondFactor() {
    Admin creator = admins.create(AdminRole.SUPER_ADMIN);
    Auth.Tokens tokens = admins.login(api, creator);
    String email = AccountFixtures.uniqueEmail();

    Response created =
        api.post(
            "/api/v1/admin/accounts",
            map("email", email, "firstName", "New", "lastName", "Admin", "role", "SUPPORT_AGENT"),
            "Authorization",
            tokens.bearer());

    assertThat(created.status()).isEqualTo(201);
    String token = mailbox.latest(email, DeliveryTemplate.ADMIN_INVITATION).orElseThrow().secret();
    assertThat(
            api.post("/api/v1/admin/auth/login", map("email", email, "password", PASSWORD))
                .status())
        .isEqualTo(401);
    Response set =
        api.post(
            "/api/v1/admin/auth/password/reset",
            map("token", token, "newPassword", "Brand-New-Passw0rd"));
    assertThat(set.status()).isEqualTo(204);
    Response login =
        api.post("/api/v1/admin/auth/login", map("email", email, "password", "Brand-New-Passw0rd"));
    assertThat(login.status()).isEqualTo(200);
    assertThat(login.json().path("mfaEnrolled").asBoolean()).isFalse();
    assertThat(audit.byActor("admin.account.created", creator.id().value())).isEqualTo(1);
    assertThat(
            roles.rolesOf(
                new com.zellastay.identity.api.AccountId(
                    UUID.fromString(created.json().path("id").asString()))))
        .containsExactly(AdminRole.SUPPORT_AGENT);
  }
}
