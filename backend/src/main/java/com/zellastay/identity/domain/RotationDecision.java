package com.zellastay.identity.domain;

/** What to do with a presented refresh token. */
public enum RotationDecision {
  ROTATE,
  RETRY_WITHIN_GRACE,
  REUSED,
  INVALID
}
