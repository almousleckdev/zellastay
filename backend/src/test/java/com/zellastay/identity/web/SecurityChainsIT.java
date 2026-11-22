package com.zellastay.identity.web;

import static com.zellastay.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.support.AccountFixtures;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SecurityChainsIT {

  @Autowired AccountFixtures fixtures;

  ApiClient api;

  private Auth.Tokens customer() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.CUSTOMER, email);
    return Auth.customer(api, email);
  }

  private Auth.Tokens vendor() {
    String email = AccountFixtures.uniqueEmail();
    fixtures.active(AccountKind.VENDOR_USER, email);
    return Auth.vendor(api, email);
  }

  @Test
  void anonymousCallersGetTheStandardUnauthenticatedProblem() {
    Response response = api.get("/api/v1/me/sessions");

    assertThat(response.status()).isEqualTo(401);
    assertThat(response.header("Content-Type")).startsWith("application/problem+json");
    assertThat(response.header("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(response.code()).isEqualTo("auth.unauthenticated");
    assertThat(response.json().path("requestId").asString()).isNotBlank();
    assertThat(response.body()).doesNotContain("Exception").doesNotContain("trace");
  }

  @Test
  void aCustomerTokenWorksOnCustomerRoutesOnly() {
    Auth.Tokens token = customer();

    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(token.access())).status())
        .isEqualTo(200);
    assertThat(
            api.get("/api/v1/vendor/me/sessions", "Authorization", bearer(token.access())).status())
        .isEqualTo(401);
    assertThat(
            api.get("/api/v1/admin/me/sessions", "Authorization", bearer(token.access())).status())
        .isEqualTo(401);
  }

  @Test
  void aVendorTokenWorksOnVendorRoutesOnly() {
    Auth.Tokens token = vendor();

    assertThat(
            api.get("/api/v1/vendor/me/sessions", "Authorization", bearer(token.access())).status())
        .isEqualTo(200);
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(token.access())).status())
        .isEqualTo(401);
    assertThat(
            api.get("/api/v1/admin/me/sessions", "Authorization", bearer(token.access())).status())
        .isEqualTo(401);
  }

  @Test
  void malformedAndForgedTokensAreRejectedUniformly() {
    for (String header :
        new String[] {
          "Bearer garbage", "Bearer ", "Basic abc", "bearer lowercase", "Bearer a.b.c"
        }) {
      Response response = api.get("/api/v1/me/sessions", "Authorization", header);
      assertThat(response.status()).as(header).isEqualTo(401);
      assertThat(response.code()).isEqualTo("auth.unauthenticated");
    }
  }

  @Test
  void aRevokedSessionStopsWorkingImmediately() {
    Auth.Tokens token = customer();
    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(token.access())).status())
        .isEqualTo(200);

    api.post("/api/v1/auth/logout", ApiClient.map("refreshToken", token.refresh()));

    assertThat(api.get("/api/v1/me/sessions", "Authorization", bearer(token.access())).status())
        .isEqualTo(401);
  }

  @Test
  void routesNobodyRegisteredAreDeniedEvenWhenAuthenticated() {
    Auth.Tokens token = vendor();

    Response unregistered =
        api.get("/api/v1/vendor/unregistered", "Authorization", bearer(token.access()));
    Response anonymous = api.get("/api/v1/vendor/unregistered");

    assertThat(unregistered.status()).isEqualTo(403);
    assertThat(unregistered.code()).isEqualTo("auth.forbidden");
    assertThat(anonymous.status()).isEqualTo(401);
  }

  @Test
  void unknownPathsOutsideTheApiStillReturnTheStandardNotFound() {
    Response response = api.get("/no/such/route");

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.code()).isEqualTo("resource.not-found");
  }

  @Test
  void securityHeadersAreSet() {
    Response response = api.get("/api/v1/me/sessions");

    assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.header("Cache-Control")).contains("no-store");
  }
}
