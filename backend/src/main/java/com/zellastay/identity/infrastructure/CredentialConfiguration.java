package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.LockoutPolicy;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.PasswordPolicy;
import com.zellastay.identity.domain.SecretGenerator;
import com.zellastay.identity.domain.SecretHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Passwords and secrets: how they are hashed, generated, checked and when logins lock. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class CredentialConfiguration {

  private static final String DENYLIST_RESOURCE = "identity/password-denylist.txt";

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
  PasswordPolicy passwordPolicy() {
    return new PasswordPolicy(PasswordDenylist.fromClasspath(DENYLIST_RESOURCE));
  }

  @Bean
  LockoutPolicy lockoutPolicy(IdentityProperties properties) {
    IdentityProperties.Lockout lockout = properties.lockout();
    return new LockoutPolicy(lockout.threshold(), lockout.baseDuration(), lockout.maxDuration());
  }
}
