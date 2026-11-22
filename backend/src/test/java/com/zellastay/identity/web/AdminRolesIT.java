package com.zellastay.identity.web;

import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.AuditEvents;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Granting and revoking admin roles. */
@IntegrationTest
class AdminRolesIT {

  @Autowired AdminFixtures admins;
  @Autowired AccountFixtures fixtures;
  @Autowired AdminRoleRepository roles;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuditEvents audit;

  ApiClient api;

  @Test
  void rolesCanBeGrantedAndRevokedButTheLastSuperAdminIsProtected() {
    Admin superAdmin = admins.create(AdminRole.SUPER_ADMIN);
    Admin target = admins.create(AdminRole.SUPPORT_AGENT);
    Auth.Tokens tokens = admins.login(api, superAdmin);
    String base = "/api/v1/admin/accounts/" + target.id().value() + "/roles";

    Response grant = api.post(base, map("role", "FINANCE_ADMIN"), "Authorization", tokens.bearer());
    Response revoke = api.delete(base + "/SUPPORT_AGENT", "Authorization", tokens.bearer());

    assertThat(grant.status()).isEqualTo(204);
    assertThat(revoke.status()).isEqualTo(204);
    assertThat(roles.rolesOf(target.id())).containsExactly(AdminRole.FINANCE_ADMIN);
    assertThat(audit.byActor("admin.role.granted", superAdmin.id().value())).isEqualTo(1);
    assertThat(audit.byActor("admin.role.revoked", superAdmin.id().value())).isEqualTo(1);

    jdbc.update(
        "delete from ident_admin_role where role = 'SUPER_ADMIN' and account_id <> ?",
        superAdmin.id().value());
    Response last =
        api.delete(
            "/api/v1/admin/accounts/" + superAdmin.id().value() + "/roles/SUPER_ADMIN",
            "Authorization",
            tokens.bearer());
    assertThat(last.status()).isEqualTo(422);
    assertThat(roles.rolesOf(superAdmin.id())).contains(AdminRole.SUPER_ADMIN);
  }

  @Test
  void roleChangesTargetAdminsOnly() {
    Auth.Tokens tokens = admins.loginAsSuperAdmin(api);
    var customer = fixtures.active(AccountKind.CUSTOMER, AccountFixtures.uniqueEmail());

    Response response =
        api.post(
            "/api/v1/admin/accounts/" + customer.id().value() + "/roles",
            map("role", "SUPER_ADMIN"),
            "Authorization",
            tokens.bearer());

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.code()).isEqualTo("identity.account-not-found");
  }
}
