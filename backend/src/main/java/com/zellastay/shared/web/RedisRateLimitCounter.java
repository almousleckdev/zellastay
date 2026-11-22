package com.zellastay.shared.web;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRateLimitCounter implements RateLimitCounter {

  private static final String PREFIX = "ratelimit:";

  private final StringRedisTemplate redis;

  RedisRateLimitCounter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public RateLimitHit hit(String key, Duration window) {
    String redisKey = PREFIX + key;
    Long count = redis.opsForValue().increment(redisKey);
    if (count == null) {
      throw new IllegalStateException("redis returned no counter value");
    }
    if (count == 1) {
      redis.expire(redisKey, window);
    }
    Long millis = redis.getExpire(redisKey, java.util.concurrent.TimeUnit.MILLISECONDS);
    if (millis == null || millis < 0) {
      redis.expire(redisKey, window);
      return new RateLimitHit(count, window);
    }
    return new RateLimitHit(count, Duration.ofMillis(millis));
  }
}
