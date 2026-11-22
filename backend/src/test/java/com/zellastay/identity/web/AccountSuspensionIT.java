package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminRole;
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

/** Suspending and reinstating accounts through the admin API. */
@IntegrationTest
class AccountSuspensionIT {

  @Autowired AdminFixtures admins;
  @Autowired AccountFixtures fixtures;
  @Autowired AuditEvents audit;

  ApiClient api;

  @Test
  void suspendingACustomerSignsThemOutAtOnceAndBlocksLoginUntilReinstated() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    Auth.Tokens adminTokens = admins.login(api, admin);
    String email = AccountFixtures.uniqueEmail();
    var customer = fixtures.active(AccountKind.CUSTOMER, email);
    Auth.Tokens session = Auth.customer(api, email);
    String id = customer.id().value().toString();

    Response suspended =
        api.post(
            "/api/v1/admin/accounts/" + id + "/suspension",
            map("reasonCode", "FRAUD_SUSPECTED"),
            "Authorization",
            adminTokens.bearer());

    assertThat(suspended.status()).isEqualTo(204);
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(session.access())).status())
        .isEqualTo(401);
    Response login = api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD));
    assertThat(login.status()).isEqualTo(403);
    assertThat(login.code()).isEqualTo("identity.account-suspended");
    assertThat(audit.byActor("account.suspended", admin.id().value())).isEqualTo(1);

    Response reinstated =
        api.delete(
            "/api/v1/admin/accounts/" + id + "/suspension", "Authorization", adminTokens.bearer());

    assertThat(reinstated.status()).isEqualTo(204);
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(200);
    assertThat(audit.byActor("account.reinstated", admin.id().value())).isEqualTo(1);
  }

  @Test
  void anAdminCannotSuspendThemselvesAndUnknownAccountsLookMissing() {
    Admin admin = admins.create(AdminRole.SUPER_ADMIN);
    Auth.Tokens tokens = admins.login(api, admin);

    Response self =
        api.post(
            "/api/v1/admin/accounts/" + admin.id().value() + "/suspension",
            map("reasonCode", "TEST"),
            "Authorization",
            tokens.bearer());
    Response missing =
        api.post(
            "/api/v1/admin/accounts/" + UUID.randomUUID() + "/suspension",
            map("reasonCode", "TEST"),
            "Authorization",
            tokens.bearer());

    assertThat(self.status()).isEqualTo(422);
    assertThat(self.code()).isEqualTo("identity.illegal-account-state");
    assertThat(missing.status()).isEqualTo(404);
    assertThat(missing.code()).isEqualTo("identity.account-not-found");
  }

  @Test
  void aMalformedReasonCodeIsRejected() {
    Auth.Tokens tokens = admins.loginAsSuperAdmin(api);

    Response response =
        api.post(
            "/api/v1/admin/accounts/" + UUID.randomUUID() + "/suspension",
            map("reasonCode", "free text with spaces"),
            "Authorization",
            tokens.bearer());

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.code()).isEqualTo("validation.failed");
  }
}
