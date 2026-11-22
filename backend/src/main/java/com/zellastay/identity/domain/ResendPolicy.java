package com.zellastay.identity.domain;

import com.zellastay.identity.api.ResendTooSoonException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Cooldown between sends and an hourly cap, so verification messages cannot be pumped. */
public final class ResendPolicy {

  private ResendPolicy() {}

  public static void check(
      Optional<Instant> lastIssuedAt,
      int issuedInLastHour,
      Instant now,
      Duration cooldown,
      int maxPerHour) {
    if (lastIssuedAt.isPresent() && now.isBefore(lastIssuedAt.get().plus(cooldown))) {
      throw new ResendTooSoonException();
    }
    if (issuedInLastHour >= maxPerHour) {
      throw new ResendTooSoonException("hourly send cap reached");
    }
  }
}
