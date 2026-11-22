package com.zellastay.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AudienceRoutesTest {

  @Test
  void carriesTheAudienceAndPatterns() {
    AudienceRoutes routes = AudienceRoutes.of(TokenAudience.ADMIN, "/a/**", "/b");

    assertThat(routes.audience()).isEqualTo(TokenAudience.ADMIN);
    assertThat(routes.patterns()).containsExactly("/a/**", "/b");
  }

  @Test
  void patternsAreImmutable() {
    AudienceRoutes routes = AudienceRoutes.of(TokenAudience.VENDOR, "/a/**");

    assertThatThrownBy(() -> routes.patterns().add("/x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsAnEmptyRegistration() {
    assertThatThrownBy(() -> AudienceRoutes.of(TokenAudience.CUSTOMER))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
