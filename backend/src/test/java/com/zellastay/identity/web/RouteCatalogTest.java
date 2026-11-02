package com.zellastay.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.shared.security.AudienceRoutes;
import com.zellastay.shared.security.TokenAudience;
import com.zellastay.support.IntegrationTest;
import com.zellastay.support.RouteCatalog;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Enumerates every route the application serves. A new endpoint fails this test until it is either
 * registered with an audience's security chain or added to the explicit public list below.
 */
@IntegrationTest
class RouteCatalogTest {

  private static final Set<String> PUBLIC_BY_DESIGN =
      Set.of(
          "/api/v1/auth/register",
          "/api/v1/auth/verify",
          "/api/v1/auth/verify/resend",
          "/api/v1/auth/login",
          "/api/v1/auth/refresh",
          "/api/v1/auth/logout",
          "/api/v1/auth/password/forgot",
          "/api/v1/auth/password/reset",
          "/api/v1/vendor/auth/register",
          "/api/v1/vendor/auth/verify",
          "/api/v1/vendor/auth/verify/resend",
          "/api/v1/vendor/auth/login",
          "/api/v1/vendor/auth/refresh",
          "/api/v1/vendor/auth/logout",
          "/api/v1/vendor/auth/password/forgot",
          "/api/v1/vendor/auth/password/reset",
          "/api/v1/admin/auth/refresh",
          "/api/v1/admin/auth/logout",
          "/api/v1/admin/auth/password/forgot",
          "/api/v1/admin/auth/password/reset",
          "/api/v1/admin/auth/login",
          "/api/v1/admin/auth/mfa/verify",
          "/api/v1/admin/auth/mfa/enroll");

  @Autowired RequestMappingHandlerMapping mappings;
  @Autowired List<AudienceRoutes> audienceRoutes;

  private Set<String> mappedRoutes() {
    Set<String> routes = new HashSet<>();
    mappings
        .getHandlerMethods()
        .keySet()
        .forEach(info -> routes.addAll(info.getPathPatternsCondition().getPatternValues()));
    return routes;
  }

  @Test
  void everyServedRouteIsProtectedOrDeliberatelyPublic() {
    Map<TokenAudience, List<String>> allowed = new EnumMap<>(TokenAudience.class);
    audienceRoutes.forEach(
        routes ->
            allowed
                .computeIfAbsent(routes.audience(), a -> new java.util.ArrayList<>())
                .addAll(routes.patterns()));

    List<String> problems =
        RouteCatalog.unprotected(mappedRoutes(), allowed, PUBLIC_BY_DESIGN, Set.of("/error"));

    assertThat(problems).isEmpty();
    assertThat(mappedRoutes()).contains("/api/v1/me/sessions", "/api/v1/auth/login");
  }
}
