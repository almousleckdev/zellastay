package com.zellastay.identity.web;

import com.zellastay.shared.security.AudienceRoutes;
import com.zellastay.shared.security.TokenAudience;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The authenticated routes identity itself owns, one set per audience. */
@Configuration(proxyBeanMethods = false)
class IdentityRoutes {

  @Bean
  AudienceRoutes customerIdentityRoutes() {
    return AudienceRoutes.of(TokenAudience.CUSTOMER, "/api/v1/me/**");
  }

  @Bean
  AudienceRoutes vendorIdentityRoutes() {
    return AudienceRoutes.of(TokenAudience.VENDOR, "/api/v1/vendor/me/**");
  }

  @Bean
  AudienceRoutes adminIdentityRoutes() {
    return AudienceRoutes.of(
        TokenAudience.ADMIN, "/api/v1/admin/me/**", "/api/v1/admin/accounts/**");
  }
}
