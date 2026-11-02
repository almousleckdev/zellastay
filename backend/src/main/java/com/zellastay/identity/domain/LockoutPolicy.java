package com.zellastay.identity.domain;

import java.time.Duration;

/** Progressive lockout: from {@code threshold} failures on, the lock doubles up to {@code max}. */
public record LockoutPolicy(int threshold, Duration base, Duration max) {

  /** Lock length after {@code failures} consecutive failures, or zero below the threshold. */
  public Duration lockFor(int failures) {
    if (failures < threshold) {
      return Duration.ZERO;
    }
    int doublings = Math.min(failures - threshold, 30);
    Duration duration = base.multipliedBy(1L << doublings);
    return duration.compareTo(max) > 0 || duration.isNegative() ? max : duration;
  }
}
