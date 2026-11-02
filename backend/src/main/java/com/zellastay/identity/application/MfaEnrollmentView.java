package com.zellastay.identity.application;

/** What the admin needs to add the account to an authenticator app. Shown once. */
public record MfaEnrollmentView(String secret, String otpauthUri) {

  @Override
  public String toString() {
    return "MfaEnrollmentView[]";
  }
}
