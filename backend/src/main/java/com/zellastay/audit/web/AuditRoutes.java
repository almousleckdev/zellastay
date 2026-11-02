package com.zellastay.audit.web;

import com.zellastay.shared.security.AudienceRoutes;
import com.zellastay.shared.security.TokenAudience;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuditRoutes {

  @Bean
  AudienceRoutes auditAdminRoutes() {
    return new AudienceRoutes() {
      @Override
      public TokenAudience audience() {
        return TokenAudience.ADMIN;
      }

      @Override
      public List<String> patterns() {
        return List.of("/api/v1/admin/audit-events");
      }
    };
  }
}
