package com.zellastay.identity.domain;

import com.zellastay.shared.security.TokenAudience;
import java.time.Duration;
import java.util.Map;

public record TokenSettings(
    Duration accessTtl, Duration refreshReuseGrace, Map<TokenAudience, SessionLifetime> sessions) {

  public SessionLifetime lifetime(TokenAudience audience) {
    return sessions.get(audience);
  }
}
