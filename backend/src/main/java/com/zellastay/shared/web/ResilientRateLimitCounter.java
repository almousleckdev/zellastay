package com.zellastay.shared.web;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Uses Redis, and the in-memory counter when Redis fails, so limiting never switches off. */
class ResilientRateLimitCounter implements RateLimitCounter {

  private static final Logger LOG = LoggerFactory.getLogger(ResilientRateLimitCounter.class);

  private final RateLimitCounter primary;
  private final RateLimitCounter fallback;

  ResilientRateLimitCounter(RateLimitCounter primary, RateLimitCounter fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  @Override
  public Hit hit(String key, Duration window) {
    try {
      return primary.hit(key, window);
    } catch (RuntimeException e) {
      LOG.warn(
          "rate limit store unavailable, using local counters ({})", e.getClass().getSimpleName());
      return fallback.hit(key, window);
    }
  }
}
