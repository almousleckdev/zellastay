package com.zellastay.support;

import static com.zellastay.support.AccountFixtures.PASSWORD;
import static com.zellastay.support.ApiClient.map;

import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient.Response;

/** Calls the two steps of admin sign-in on the real API. */
public final class AdminAuthCalls {

  private AdminAuthCalls() {}

  public static Response password(ApiClient api, Admin admin) {
    return api.post("/api/v1/admin/auth/login", map("email", admin.email(), "password", PASSWORD));
  }

  public static String ticket(Response passwordStep) {
    return passwordStep.json().path("mfaTicket").asString();
  }

  public static Response verify(ApiClient api, String ticket, String code) {
    return api.post("/api/v1/admin/auth/mfa/verify", map("mfaTicket", ticket, "code", code));
  }
}
