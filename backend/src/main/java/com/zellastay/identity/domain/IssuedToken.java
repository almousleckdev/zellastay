package com.zellastay.identity.domain;

import java.time.Instant;

/** A signed access token and the moment it stops being valid. */
public record IssuedToken(String value, Instant expiresAt) {

  @Override
  public String toString() {
    return "IssuedToken[expiresAt=" + expiresAt + "]";
  }
}
