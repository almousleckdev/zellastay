package com.zellastay.shared.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Per-instance fixed-window counter; the degraded mode when Redis is unavailable. */
class InMemoryRateLimitCounter implements RateLimitCounter {

  private static final int CLEANUP_THRESHOLD = 10_000;

  private record Window(Instant start, long count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
  private final Clock clock;

  InMemoryRateLimitCounter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Hit hit(String key, Duration window) {
    Instant now = clock.instant();
    if (windows.size() > CLEANUP_THRESHOLD) {
      windows.values().removeIf(w -> !now.isBefore(w.start().plus(window)));
    }
    Window current =
        windows.merge(
            key,
            new Window(now, 1),
            (existing, fresh) ->
                now.isBefore(existing.start().plus(window))
                    ? new Window(existing.start(), existing.count() + 1)
                    : fresh);
    return new Hit(current.count(), Duration.between(now, current.start().plus(window)));
  }
}
