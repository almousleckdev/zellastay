package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import com.zellastay.shared.security.TokenAudience;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Issues and verifies short-lived, audience-bound access tokens. */
public interface AccessTokens {

  record IssuedToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
      return "IssuedToken[expiresAt=" + expiresAt + "]";
    }
  }

  record TokenClaims(
      AccountId accountId,
      TokenAudience audience,
      UUID sessionId,
      boolean mfaVerified,
      Instant expiresAt) {}

  IssuedToken issue(
      AccountId accountId,
      TokenAudience audience,
      UUID sessionId,
      boolean mfaVerified,
      Instant now);

  /** Empty for anything that is not a valid, unexpired token of the expected audience. */
  Optional<TokenClaims> parse(String token, TokenAudience expectedAudience, Instant now);
}
