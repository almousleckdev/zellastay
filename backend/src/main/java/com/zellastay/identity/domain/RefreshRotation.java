package com.zellastay.identity.domain;

import java.time.Duration;
import java.time.Instant;

/** Decides what presenting a refresh token means; persistence is the caller's job. */
public final class RefreshRotation {

  public enum Decision {
    ROTATE,
    RETRY_WITHIN_GRACE,
    REUSED,
    INVALID
  }

  private RefreshRotation() {}

  public static Decision decide(Session session, RefreshToken token, Instant now, Duration grace) {
    if (!token.sessionId().equals(session.id()) || !session.isActive(now)) {
      return Decision.INVALID;
    }
    if (token.usedAt() == null) {
      return Decision.ROTATE;
    }
    boolean withinGrace = token.replacedBy() != null && !now.isAfter(token.usedAt().plus(grace));
    return withinGrace ? Decision.RETRY_WITHIN_GRACE : Decision.REUSED;
  }
}
