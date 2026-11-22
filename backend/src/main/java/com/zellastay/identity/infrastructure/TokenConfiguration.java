package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.identity.domain.SessionLifetime;
import com.zellastay.identity.domain.SessionStatusCache;
import com.zellastay.identity.domain.TokenSettings;
import com.zellastay.shared.security.TokenAudience;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Access tokens, session lifetimes and the session-status cache. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class TokenConfiguration {

  @Bean
  AccessTokens accessTokens(IdentityProperties properties) {
    IdentityProperties.Tokens tokens = properties.tokens();
    return new EcAccessTokens(
        tokens.issuer(),
        tokens.signingKeyId(),
        tokens.signingKey(),
        tokens.retiredPublicKeys() == null ? Map.of() : tokens.retiredPublicKeys(),
        tokens.accessTtl());
  }

  @Bean
  TokenSettings tokenSettings(IdentityProperties properties) {
    IdentityProperties.Tokens tokens = properties.tokens();
    Map<TokenAudience, SessionLifetime> lifetimes = new EnumMap<>(TokenAudience.class);
    tokens
        .sessions()
        .forEach(
            (audience, lifetime) ->
                lifetimes.put(audience, new SessionLifetime(lifetime.absolute(), lifetime.idle())));
    return new TokenSettings(tokens.accessTtl(), tokens.refreshReuseGrace(), lifetimes);
  }

  @Bean
  SessionStatusCache sessionStatusCache(StringRedisTemplate redis, IdentityProperties properties) {
    return new RedisSessionStatusCache(redis, properties.tokens().sessionCacheTtl());
  }
}
