package com.zellastay.identity.web;

import com.zellastay.identity.application.AdminSession;
import com.zellastay.identity.application.TokenPair;
import java.util.List;

/**
 * Response with the admin token pair and any new recovery codes. Secrets never appear in
 * toString().
 */
record AdminTokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    List<String> recoveryCodes) {

  static AdminTokenResponse from(AdminSession session) {
    TokenPair pair = session.tokens();
    return new AdminTokenResponse(
        pair.accessToken(),
        "Bearer",
        pair.expiresInSeconds(),
        pair.refreshToken(),
        session.recoveryCodes());
  }

  @Override
  public String toString() {
    return "AdminTokenResponse[]";
  }
}
