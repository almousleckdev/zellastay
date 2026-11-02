package com.zellastay.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.shared.security.TokenAudience;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouteCatalogUnitTest {

  private static final Map<TokenAudience, List<String>> ALLOWED =
      Map.of(
          TokenAudience.CUSTOMER, List.of("/api/v1/me/**"),
          TokenAudience.VENDOR, List.of("/api/v1/vendor/me/**"),
          TokenAudience.ADMIN, List.of("/api/v1/admin/me/**"));

  private static List<String> check(String... routes) {
    return RouteCatalog.unprotected(
        Set.of(routes), ALLOWED, Set.of("/api/v1/auth/login"), Set.of("/error"));
  }

  @Test
  void registeredAndAllowListedRoutesPass() {
    assertThat(
            check(
                "/api/v1/me/sessions/{id}",
                "/api/v1/vendor/me/password",
                "/api/v1/admin/me/sessions",
                "/api/v1/auth/login",
                "/error"))
        .isEmpty();
  }

  @Test
  void anUnregisteredAudienceRouteIsReported() {
    assertThat(check("/api/v1/vendor/properties")).singleElement().asString().contains("VENDOR");
    assertThat(check("/api/v1/admin/accounts/{id}")).hasSize(1);
    assertThat(check("/api/v1/bookings/{id}")).hasSize(1);
  }

  @Test
  void aNewAnonymousRouteMustBeAddedToThePublicListOnPurpose() {
    assertThat(check("/api/v1/auth/secret-backdoor")).singleElement().asString().contains("public");
    assertThat(check("/api/v1/public/search")).hasSize(1);
  }

  @Test
  void routesOutsideTheApiNeedExplicitApproval() {
    assertThat(check("/internal/debug")).singleElement().asString().contains("outside /api");
  }
}
