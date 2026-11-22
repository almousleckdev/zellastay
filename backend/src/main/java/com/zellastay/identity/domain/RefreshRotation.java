package com.zellastay.identity.domain;

import java.time.Duration;
import java.time.Instant;

/** Decides what presenting a refresh token means; persistence is the caller's job. */
public final class RefreshRotation {

  private RefreshRotation() {}

  public static RotationDecision decide(
      Session session, RefreshToken token, Instant now, Duration grace) {
    if (!token.sessionId().equals(session.id()) || !session.isActive(now)) {
      return RotationDecision.INVALID;
    }
    if (token.usedAt() == null) {
      return RotationDecision.ROTATE;
    }
    boolean withinGrace = token.replacedBy() != null && !now.isAfter(token.usedAt().plus(grace));
    return withinGrace ? RotationDecision.RETRY_WITHIN_GRACE : RotationDecision.REUSED;
  }
}
