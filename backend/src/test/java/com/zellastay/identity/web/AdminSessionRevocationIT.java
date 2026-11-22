package com.zellastay.identity.web;

import static com.zellastay.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Signing another account out everywhere. */
@IntegrationTest
class AdminSessionRevocationIT {

  @Autowired AdminFixtures admins;
  @Autowired AccountFixtures fixtures;

  ApiClient api;

  @Test
  void anAdminCanSignAnotherAccountOutEverywhere() {
    Auth.Tokens tokens = admins.loginAsSuperAdmin(api);
    String email = AccountFixtures.uniqueEmail();
    var customer = fixtures.active(AccountKind.CUSTOMER, email);
    Auth.Tokens session = Auth.customer(api, email);

    Response response =
        api.delete(
            "/api/v1/admin/accounts/" + customer.id().value() + "/sessions",
            "Authorization",
            tokens.bearer());

    assertThat(response.status()).isEqualTo(204);
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(session.access())).status())
        .isEqualTo(401);
  }
}
