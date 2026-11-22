package com.zellastay.shared.web;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

  @Bean
  RateLimitCounter rateLimitCounter(StringRedisTemplate redis, Clock clock) {
    return new ResilientRateLimitCounter(
        new RedisRateLimitCounter(redis), new InMemoryRateLimitCounter(clock));
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      RateLimitCounter counter, RateLimitProperties properties, ProblemResponseWriter writer) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(new RateLimitFilter(counter, properties, writer));
    registration.setOrder(new RateLimitFilter(counter, properties, writer).getOrder());
    return registration;
  }
}
