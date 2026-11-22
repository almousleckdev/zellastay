package com.zellastay.shared.web;

import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

@IntegrationTest
class RateLimitIT {

  @Value("${local.server.port}")
  int port;

  @Test
  void authenticationEndpointsAreThrottledPerAddressWithRetryAfterAndTheStandardProblem() {
    ApiClient api = new ApiClient(port, "203.0.113.77");
    Response last = null;
    int throttledAt = 0;
    for (int i = 1; i <= 25 && throttledAt == 0; i++) {
      last = api.post("/api/v1/auth/login", map("email", "nobody@example.com", "password", "x"));
      if (last.status() == 429) {
        throttledAt = i;
      }
    }

    assertThat(throttledAt).isEqualTo(21);
    assertThat(last.header("Retry-After")).matches("\\d+");
    assertThat(last.header("Content-Type")).startsWith("application/problem+json");
    assertThat(last.code()).isEqualTo("rate.limited");
    assertThat(last.json().path("requestId").asString()).isNotBlank();
  }

  @Test
  void theGeneralApiPolicyIsSeparateFromTheAuthenticationPolicy() {
    ApiClient api = new ApiClient(port, "203.0.113.78");
    for (int i = 0; i < 21; i++) {
      api.post("/api/v1/auth/login", map("email", "nobody@example.com", "password", "x"));
    }

    Response other = api.get("/api/v1/me/sessions");

    assertThat(other.status()).isEqualTo(401);
  }

  @Test
  void differentAddressesDoNotShareALimit() {
    ApiClient first = new ApiClient(port, "203.0.113.79");
    ApiClient second = new ApiClient(port, "203.0.113.80");
    for (int i = 0; i < 21; i++) {
      first.post("/api/v1/auth/login", map("email", "nobody@example.com", "password", "x"));
    }

    assertThat(
            second
                .post("/api/v1/auth/login", map("email", "nobody@example.com", "password", "x"))
                .status())
        .isEqualTo(401);
  }
}
