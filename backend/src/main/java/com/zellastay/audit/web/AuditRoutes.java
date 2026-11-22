package com.zellastay.audit.web;

import com.zellastay.shared.security.AudienceRoutes;
import com.zellastay.shared.security.TokenAudience;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuditRoutes {

  @Bean
  AudienceRoutes auditAdminRoutes() {
    return AudienceRoutes.of(TokenAudience.ADMIN, "/api/v1/admin/audit-events");
  }
}
