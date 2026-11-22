package com.zellastay.identity.domain;

/** The outcome of checking a submitted secret against a challenge. */
public enum ChallengeCheckResult {
  VERIFIED,
  WRONG,
  BURNED,
  EXPIRED,
  USED
}
