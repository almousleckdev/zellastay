package com.zellastay.support;

import static com.zellastay.support.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.support.ApiClient.Response;

/** Calls the customer password-recovery endpoints of the real API. */
public final class PasswordRecoveryCalls {

  private PasswordRecoveryCalls() {}

  /** Requests a reset and returns the token the application delivered. */
  public static String requestToken(ApiClient api, TestMailbox mailbox, String email) {
    Response response = api.post("/api/v1/auth/password/forgot", map("email", email));
    assertThat(response.status()).isEqualTo(202);
    return mailbox.resetToken(email);
  }

  public static Response reset(ApiClient api, String token, String password) {
    return api.post("/api/v1/auth/password/reset", map("token", token, "newPassword", password));
  }
}
