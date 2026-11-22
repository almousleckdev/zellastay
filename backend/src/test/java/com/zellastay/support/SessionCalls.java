package com.zellastay.support;

import com.zellastay.support.ApiClient.Response;

/** Calls the customer self-service session endpoints of the real API. */
public final class SessionCalls {

  private SessionCalls() {}

  public static Response list(ApiClient api, Auth.Tokens tokens) {
    return api.get("/api/v1/me/sessions", "Authorization", tokens.bearer());
  }
}
