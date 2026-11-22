package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import java.time.Instant;

/** An authenticator enrolment; {@code enrolledAt} stays null until the first code is confirmed. */
public record MfaEnrollment(
    AccountId accountId, String secretCiphertext, Instant enrolledAt, long lastUsedStep) {

  public boolean confirmed() {
    return enrolledAt != null;
  }

  @Override
  public String toString() {
    return "MfaEnrollment[accountId=" + accountId + ", confirmed=" + confirmed() + "]";
  }
}
