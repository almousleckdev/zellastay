package com.zellastay.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimitingTest {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-02T10:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void theInMemoryCounterCountsPerKeyAndRestartsAfterTheWindow() {
    MutableClock clock = new MutableClock();
    InMemoryRateLimitCounter counter = new InMemoryRateLimitCounter(clock);

    assertThat(counter.hit("a", WINDOW).count()).isEqualTo(1);
    assertThat(counter.hit("a", WINDOW).count()).isEqualTo(2);
    assertThat(counter.hit("b", WINDOW).count()).isEqualTo(1);
    clock.advance(Duration.ofSeconds(59));
    RateLimitHit late = counter.hit("a", WINDOW);
    assertThat(late.count()).isEqualTo(3);
    assertThat(late.remaining()).isEqualTo(Duration.ofSeconds(1));
    clock.advance(Duration.ofSeconds(2));
    assertThat(counter.hit("a", WINDOW).count()).isEqualTo(1);
  }

  @Test
  void whenRedisFailsTheLocalCounterKeepsLimitingInsteadOfSwitchingLimitingOff() {
    RateLimitCounter broken =
        (key, window) -> {
          throw new IllegalStateException("redis down");
        };
    ResilientRateLimitCounter counter =
        new ResilientRateLimitCounter(broken, new InMemoryRateLimitCounter(new MutableClock()));

    assertThat(counter.hit("k", WINDOW).count()).isEqualTo(1);
    assertThat(counter.hit("k", WINDOW).count()).isEqualTo(2);
  }

  @Test
  void aWorkingPrimaryIsPreferred() {
    RateLimitCounter primary = (key, window) -> new RateLimitHit(42, window);
    ResilientRateLimitCounter counter =
        new ResilientRateLimitCounter(primary, new InMemoryRateLimitCounter(new MutableClock()));

    assertThat(counter.hit("k", WINDOW).count()).isEqualTo(42);
  }
}
