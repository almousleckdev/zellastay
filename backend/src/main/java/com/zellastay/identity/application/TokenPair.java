package com.zellastay.identity.application;

import java.time.Instant;

/** What a successful login or refresh returns. The refresh token is a secret. */
public record TokenPair(
    String accessToken, Instant accessExpiresAt, long expiresInSeconds, String refreshToken) {

  @Override
  public String toString() {
    return "TokenPair[accessExpiresAt=" + accessExpiresAt + "]";
  }
}
