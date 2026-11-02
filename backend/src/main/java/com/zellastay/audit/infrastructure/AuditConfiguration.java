package com.zellastay.audit.infrastructure;

import com.zellastay.audit.domain.AuditEntryValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AuditProperties.class)
class AuditConfiguration {

  @Bean
  AuditEntryValidator auditEntryValidator() {
    return new AuditEntryValidator();
  }
}
