package com.zellastay.identity.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stand-in for the notification module; staging and production must supply the real one. */
@Configuration(proxyBeanMethods = false)
class DevelopmentDeliveryConfiguration {

  @Bean
  @ConditionalOnExpression(
      "'${zellastay.environment}' == 'LOCAL' or '${zellastay.environment}' == 'CI'")
  InMemoryVerificationDelivery inMemoryVerificationDelivery() {
    return new InMemoryVerificationDelivery();
  }
}
