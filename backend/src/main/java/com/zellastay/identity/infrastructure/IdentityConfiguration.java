package com.zellastay.identity.infrastructure;

import com.zellastay.identity.application.MfaSettings;
import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.identity.domain.ChallengeSettings;
import com.zellastay.identity.domain.LockoutPolicy;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.PasswordPolicy;
import com.zellastay.identity.domain.SecretCipher;
import com.zellastay.identity.domain.SecretGenerator;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.SessionLifetime;
import com.zellastay.identity.domain.SessionStatusCache;
import com.zellastay.identity.domain.TokenSettings;
import com.zellastay.shared.security.TokenAudience;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

  @Bean
  PasswordHasher passwordHasher(IdentityProperties properties) {
    IdentityProperties.Hashing hashing = properties.hashing();
    return new Argon2PasswordHasher(
        hashing.memoryKib(), hashing.iterations(), hashing.parallelism());
  }

  @Bean
  SecretHasher secretHasher(IdentityProperties properties) {
    return new DefaultSecretHasher(properties.challenges().pepper());
  }

  @Bean
  SecretGenerator secretGenerator() {
    return new SecureSecretGenerator();
  }

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

  @Bean
  MfaSettings mfaSettings(IdentityProperties properties) {
    IdentityProperties.Mfa mfa = properties.mfa();
    return new MfaSettings(mfa.ticketTtl(), mfa.maxAttempts(), mfa.issuerLabel());
  }

  @Bean
  SecretCipher secretCipher(IdentityProperties properties) {
    return new AesGcmSecretCipher(
        properties.mfa().encryptionKeyId(), properties.mfa().encryptionKey());
  }

  @Bean
  PasswordPolicy passwordPolicy() {
    try (var lines =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                new ClassPathResource("identity/password-denylist.txt").getInputStream(),
                StandardCharsets.UTF_8))) {
      Set<String> denied =
          lines
              .lines()
              .map(String::strip)
              .filter(line -> !line.isEmpty() && !line.startsWith("#"))
              .map(line -> line.toLowerCase(Locale.ROOT))
              .collect(Collectors.toUnmodifiableSet());
      return new PasswordPolicy(denied);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read the password denylist", e);
    }
  }

  @Bean
  LockoutPolicy lockoutPolicy(IdentityProperties properties) {
    IdentityProperties.Lockout lockout = properties.lockout();
    return new LockoutPolicy(lockout.threshold(), lockout.baseDuration(), lockout.maxDuration());
  }

  @Bean
  ChallengeSettings challengeSettings(IdentityProperties properties) {
    IdentityProperties.Challenges c = properties.challenges();
    return new ChallengeSettings(
        c.codeTtl(), c.maxAttempts(), c.resendCooldown(), c.maxSendsPerHour(), c.resetTokenTtl());
  }

  /** Stand-in for the notification module; staging and production must supply the real one. */
  @Bean
  @ConditionalOnExpression(
      "'${zellastay.environment}' == 'LOCAL' or '${zellastay.environment}' == 'CI'")
  InMemoryVerificationDelivery inMemoryVerificationDelivery() {
    return new InMemoryVerificationDelivery();
  }
}
