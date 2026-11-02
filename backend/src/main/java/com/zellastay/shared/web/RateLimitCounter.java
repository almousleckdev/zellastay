package com.zellastay.shared.web;

import java.time.Duration;

/** Counts hits per key in a fixed window. */
interface RateLimitCounter {

  /** Result of one hit: the count in the current window and the time left in it. */
  record Hit(long count, Duration remaining) {}

  Hit hit(String key, Duration window);
}
