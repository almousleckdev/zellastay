package com.zellastay.identity.web;

/** Response with the new authenticator secret, shown once. Secrets never appear in toString(). */
record EnrollmentResponse(String secret, String otpauthUri) {

  @Override
  public String toString() {
    return "EnrollmentResponse[]";
  }
}
