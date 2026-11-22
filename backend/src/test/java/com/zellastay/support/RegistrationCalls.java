package com.zellastay.support;

import static com.zellastay.support.ApiClient.map;

import com.zellastay.support.ApiClient.Response;

/** Calls the registration endpoints of the real API. */
public final class RegistrationCalls {

  private RegistrationCalls() {}

  public static Response register(ApiClient api, String path, String email, String password) {
    return api.post(
        path,
        map(
            "email",
            email,
            "password",
            password,
            "firstName",
            "Jane",
            "lastName",
            "Doe",
            "locale",
            "fr"));
  }
}
