package com.zellastay.identity.application;

import java.util.List;

/** A finished admin sign-in; recovery codes are present only on first enrolment. */
public record AdminSession(TokenPair tokens, List<String> recoveryCodes) {

  @Override
  public String toString() {
    return "AdminSession[]";
  }
}
