package com.zellastay.support;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.map;

import com.zellastay.support.ApiClient.Response;

/** Logs in through the real endpoints and hands back the tokens. */
public final class Auth {

  public record Tokens(String access, String refresh) {}

  private Auth() {}

  public static Tokens login(ApiClient api, String path, String email) {
    Response response = api.post(path, map("email", email, "password", PASSWORD));
    if (response.status() != 200) {
      throw new IllegalStateException("login failed: " + response.status() + " " + response.body());
    }
    return new Tokens(
        response.json().path("accessToken").asString(),
        response.json().path("refreshToken").asString());
  }

  public static Tokens customer(ApiClient api, String email) {
    return login(api, "/api/v1/auth/login", email);
  }

  public static Tokens vendor(ApiClient api, String email) {
    return login(api, "/api/v1/vendor/auth/login", email);
  }
}
