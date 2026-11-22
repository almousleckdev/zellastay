package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.ChallengeSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Verification codes and reset links: lifetimes, attempt limits and resend limits. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class ChallengeConfiguration {

  @Bean
  ChallengeSettings challengeSettings(IdentityProperties properties) {
    IdentityProperties.Challenges c = properties.challenges();
    return new ChallengeSettings(
        c.codeTtl(), c.maxAttempts(), c.resendCooldown(), c.maxSendsPerHour(), c.resetTokenTtl());
  }
}
