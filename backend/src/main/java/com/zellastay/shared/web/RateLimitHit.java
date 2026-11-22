package com.zellastay.shared.web;

import java.time.Duration;

/** Result of one hit: the count in the current window and the time left in it. */
record RateLimitHit(long count, Duration remaining) {}
