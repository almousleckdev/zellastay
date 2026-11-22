package com.zellastay.identity.infrastructure;

import com.zellastay.identity.application.MfaSettings;
import com.zellastay.identity.domain.SecretCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The administrators' second factor: ticket and attempt limits, and seed encryption. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class MfaConfiguration {

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
}
