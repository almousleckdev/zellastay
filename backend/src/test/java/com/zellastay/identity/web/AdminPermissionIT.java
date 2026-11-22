package com.zellastay.identity.web;

import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Permission checks on every admin account endpoint. */
@IntegrationTest
class AdminPermissionIT {

  @Autowired AdminFixtures admins;
  @Autowired AdminRoleRepository roles;

  ApiClient api;

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
                tokens.bearer()),
            api.post(
                base + "/" + target + "/roles",
                map("role", "SUPPORT_AGENT"),
                "Authorization",
                tokens.bearer()),
            api.delete(
                base + "/" + target + "/roles/SUPPORT_AGENT", "Authorization", tokens.bearer()),
            api.post(
                base + "/" + target + "/suspension",
                map("reasonCode", "TEST"),
                "Authorization",
                tokens.bearer()),
            api.delete(base + "/" + target + "/suspension", "Authorization", tokens.bearer()),
            api.delete(base + "/" + target + "/sessions", "Authorization", tokens.bearer()));

    assertThat(responses)
        .allSatisfy(
            response -> {
              assertThat(response.status()).isEqualTo(403);
              assertThat(response.code()).isEqualTo("auth.forbidden");
            });
  }
}
