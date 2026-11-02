package com.zellastay.identity.web;

import com.zellastay.shared.security.AudienceRoutes;
import com.zellastay.shared.security.TokenAudience;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The authenticated routes identity itself owns, one set per audience. */
@Configuration(proxyBeanMethods = false)
class IdentityRoutes {

  @Bean
  AudienceRoutes customerIdentityRoutes() {
    return routes(TokenAudience.CUSTOMER, "/api/v1/me/**");
  }

  @Bean
  AudienceRoutes vendorIdentityRoutes() {
    return routes(TokenAudience.VENDOR, "/api/v1/vendor/me/**");
  }

  @Bean
  AudienceRoutes adminIdentityRoutes() {
    return routes(TokenAudience.ADMIN, "/api/v1/admin/me/**", "/api/v1/admin/accounts/**");
  }

  private static AudienceRoutes routes(TokenAudience audience, String... patterns) {
    List<String> list = List.of(patterns);
    return new AudienceRoutes() {
      @Override
      public TokenAudience audience() {
        return audience;
      }

      @Override
      public List<String> patterns() {
        return list;
      }
    };
  }
}
