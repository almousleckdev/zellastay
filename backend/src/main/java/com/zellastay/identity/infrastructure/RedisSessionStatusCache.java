package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.SessionStatusCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed session status. Any Redis problem is a miss, so the database decides instead. */
public class RedisSessionStatusCache implements SessionStatusCache {

  private static final Logger LOG = LoggerFactory.getLogger(RedisSessionStatusCache.class);
  private static final String PREFIX = "ident:session:";

  private final StringRedisTemplate redis;
  private final Duration ttl;

  public RedisSessionStatusCache(StringRedisTemplate redis, Duration ttl) {
    this.redis = redis;
    this.ttl = ttl;
  }

  @Override
  public Optional<Boolean> get(UUID sessionId) {
    try {
      String value = redis.opsForValue().get(PREFIX + sessionId);
      return value == null ? Optional.empty() : Optional.of("1".equals(value));
    } catch (RuntimeException e) {
      LOG.warn("session cache read failed ({})", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  @Override
  public void put(UUID sessionId, boolean active) {
    try {
      redis.opsForValue().set(PREFIX + sessionId, active ? "1" : "0", ttl);
    } catch (RuntimeException e) {
      LOG.warn("session cache write failed ({})", e.getClass().getSimpleName());
    }
  }

  @Override
  public void evict(UUID sessionId) {
    try {
      redis.delete(PREFIX + sessionId);
    } catch (RuntimeException e) {
      LOG.warn("session cache eviction failed ({})", e.getClass().getSimpleName());
    }
  }
}
