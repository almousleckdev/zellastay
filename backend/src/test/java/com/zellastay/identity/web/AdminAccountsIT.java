package com.zellastay.identity.web;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.bearer;
import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.identity.infrastructure.InMemoryVerificationDelivery;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class AdminAccountsIT {

  @Value("${local.server.port}")
  int port;

  @Autowired AdminFixtures admins;
  @Autowired AccountFixtures fixtures;
  @Autowired AdminRoleRepository roles;
  @Autowired JdbcTemplate jdbc;
  @Autowired InMemoryVerificationDelivery mailbox;

  ApiClient api;

  @BeforeEach
  void client() {
    api = new ApiClient(port);
  }

  private String auth(Auth.Tokens tokens) {
    return bearer(tokens.access());
  }

  private Auth.Tokens superAdmin() {
    return admins.login(api, admins.create(AdminRole.SUPER_ADMIN));
  }

  private int audited(String action, Object actorId) {
    return jdbc.queryForObject(
        "select count(*) from audit_event where action = ? and actor_id = ?",
        Integer.class,
        action,
        actorId);
  }

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
            auth(adminTokens));

    assertThat(suspended.status()).isEqualTo(204);
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(session.access())).status())
        .isEqualTo(401);
    Response login = api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD));
    assertThat(login.status()).isEqualTo(403);
    assertThat(login.code()).isEqualTo("identity.account-suspended");
    assertThat(audited("account.suspended", admin.id().value())).isEqualTo(1);

    Response reinstated =
        api.delete(
            "/api/v1/admin/accounts/" + id + "/suspension", "Authorization", auth(adminTokens));

    assertThat(reinstated.status()).isEqualTo(204);
    assertThat(api.post("/api/v1/auth/login", map("email", email, "password", PASSWORD)).status())
        .isEqualTo(200);
    assertThat(audited("account.reinstated", admin.id().value())).isEqualTo(1);
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
            auth(tokens));
    Response missing =
        api.post(
            "/api/v1/admin/accounts/" + UUID.randomUUID() + "/suspension",
            map("reasonCode", "TEST"),
            "Authorization",
            auth(tokens));

    assertThat(self.status()).isEqualTo(422);
    assertThat(self.code()).isEqualTo("identity.illegal-account-state");
    assertThat(missing.status()).isEqualTo(404);
    assertThat(missing.code()).isEqualTo("identity.account-not-found");
  }

  @Test
  void aMalformedReasonCodeIsRejected() {
    Auth.Tokens tokens = superAdmin();

    Response response =
        api.post(
            "/api/v1/admin/accounts/" + UUID.randomUUID() + "/suspension",
            map("reasonCode", "free text with spaces"),
            "Authorization",
            auth(tokens));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.code()).isEqualTo("validation.failed");
  }

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
            auth(tokens));

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
    assertThat(audited("admin.account.created", creator.id().value())).isEqualTo(1);
    assertThat(
            roles.rolesOf(
                new com.zellastay.identity.api.AccountId(
                    UUID.fromString(created.json().path("id").asString()))))
        .containsExactly(AdminRole.SUPPORT_AGENT);
  }

  @Test
  void rolesCanBeGrantedAndRevokedButTheLastSuperAdminIsProtected() {
    Admin superAdmin = admins.create(AdminRole.SUPER_ADMIN);
    Admin target = admins.create(AdminRole.SUPPORT_AGENT);
    Auth.Tokens tokens = admins.login(api, superAdmin);
    String base = "/api/v1/admin/accounts/" + target.id().value() + "/roles";

    Response grant = api.post(base, map("role", "FINANCE_ADMIN"), "Authorization", auth(tokens));
    Response revoke = api.delete(base + "/SUPPORT_AGENT", "Authorization", auth(tokens));

    assertThat(grant.status()).isEqualTo(204);
    assertThat(revoke.status()).isEqualTo(204);
    assertThat(roles.rolesOf(target.id())).containsExactly(AdminRole.FINANCE_ADMIN);
    assertThat(audited("admin.role.granted", superAdmin.id().value())).isEqualTo(1);
    assertThat(audited("admin.role.revoked", superAdmin.id().value())).isEqualTo(1);

    jdbc.update(
        "delete from ident_admin_role where role = 'SUPER_ADMIN' and account_id <> ?",
        superAdmin.id().value());
    Response last =
        api.delete(
            "/api/v1/admin/accounts/" + superAdmin.id().value() + "/roles/SUPER_ADMIN",
            "Authorization",
            auth(tokens));
    assertThat(last.status()).isEqualTo(422);
    assertThat(roles.rolesOf(superAdmin.id())).contains(AdminRole.SUPER_ADMIN);
  }

  @Test
  void anAdminWithoutThePermissionIsDeniedOnEveryAccountEndpoint() {
    Admin support = admins.create(AdminRole.SUPPORT_AGENT);
    Auth.Tokens tokens = admins.login(api, support);
    String target = UUID.randomUUID().toString();
    String base = "/api/v1/admin/accounts";

    List<Response> responses =
        List.of(
            api.post(
                base,
                map(
                    "email",
                    "x@example.com",
                    "firstName",
                    "A",
                    "lastName",
                    "B",
                    "role",
                    "SUPPORT_AGENT"),
                "Authorization",
                auth(tokens)),
            api.post(
                base + "/" + target + "/roles",
                map("role", "SUPPORT_AGENT"),
                "Authorization",
                auth(tokens)),
            api.delete(base + "/" + target + "/roles/SUPPORT_AGENT", "Authorization", auth(tokens)),
            api.post(
                base + "/" + target + "/suspension",
                map("reasonCode", "TEST"),
                "Authorization",
                auth(tokens)),
            api.delete(base + "/" + target + "/suspension", "Authorization", auth(tokens)),
            api.delete(base + "/" + target + "/sessions", "Authorization", auth(tokens)));

    assertThat(responses)
        .allSatisfy(
            response -> {
              assertThat(response.status()).isEqualTo(403);
              assertThat(response.code()).isEqualTo("auth.forbidden");
            });
  }

  @Test
  void anAdminCanSignAnotherAccountOutEverywhere() {
    Auth.Tokens tokens = superAdmin();
    String email = AccountFixtures.uniqueEmail();
    var customer = fixtures.active(AccountKind.CUSTOMER, email);
    Auth.Tokens session = Auth.customer(api, email);

    Response response =
        api.delete(
            "/api/v1/admin/accounts/" + customer.id().value() + "/sessions",
            "Authorization",
            auth(tokens));

    assertThat(response.status()).isEqualTo(204);
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(session.access())).status())
        .isEqualTo(401);
  }

  @Test
  void roleChangesTargetAdminsOnly() {
    Auth.Tokens tokens = superAdmin();
    var customer = fixtures.active(AccountKind.CUSTOMER, AccountFixtures.uniqueEmail());

    Response response =
        api.post(
            "/api/v1/admin/accounts/" + customer.id().value() + "/roles",
            map("role", "SUPER_ADMIN"),
            "Authorization",
            auth(tokens));

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.code()).isEqualTo("identity.account-not-found");
  }
}
