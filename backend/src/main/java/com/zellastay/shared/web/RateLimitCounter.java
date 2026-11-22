package com.zellastay.shared.web;

import java.time.Duration;

/** Counts hits per key in a fixed window. */
interface RateLimitCounter {

  RateLimitHit hit(String key, Duration window);
}
