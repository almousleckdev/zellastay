package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisSessionStatusCacheTest {

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final RedisSessionStatusCache cache =
      new RedisSessionStatusCache(redis, Duration.ofSeconds(30));
  private final UUID session = UUID.randomUUID();

  @Test
  void aRedisOutageIsAMissNeverAnApproval() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

    assertThat(cache.get(session)).isEmpty();
  }

  @Test
  void writesAndEvictionsSurviveAnOutageSilently() {
    when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
    when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

    assertThatCode(() -> cache.put(session, true)).doesNotThrowAnyException();
    assertThatCode(() -> cache.evict(session)).doesNotThrowAnyException();
  }

  @Test
  void storedValuesAreReadBack() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("ident:session:" + session)).thenReturn("1");

    assertThat(cache.get(session)).contains(true);
    when(values.get("ident:session:" + session)).thenReturn("0");
    assertThat(cache.get(session)).contains(false);
    when(values.get("ident:session:" + session)).thenReturn(null);
    assertThat(cache.get(session)).isEmpty();
  }
}
