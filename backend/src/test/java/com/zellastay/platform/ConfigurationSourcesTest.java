package com.zellastay.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.zellastay.ZellaStayApplication;
import com.zellastay.identity.infrastructure.IdentityProperties;
import com.zellastay.support.TestInfrastructure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class ConfigurationSourcesTest {

  @Test
  void startupFailsFastNamingTheMissingSetting() {
    assumeThat(System.getenv("ZS_DB_URL")).isNull();

    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(ZellaStayApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties("spring.config.import=", "ZS_SECRETS_DIR=/nonexistent/")
                    .run())
        .hasStackTraceContaining("Missing required settings")
        .hasStackTraceContaining("ZS_DB_URL");
  }

  @Test
  void secretsAreReadFromAMountedDirectory(@TempDir Path secrets) throws Exception {
    Map<String, String> environment = TestInfrastructure.applicationEnvironment();
    Files.writeString(secrets.resolve("ZS_DB_PASSWORD"), environment.get("ZS_DB_PASSWORD"));
    Files.writeString(secrets.resolve("ZS_REDIS_PASSWORD"), environment.get("ZS_REDIS_PASSWORD"));
    Files.writeString(
        secrets.resolve("ZS_IDENTITY_TOKEN_SIGNING_KEY"),
        environment.get("ZS_IDENTITY_TOKEN_SIGNING_KEY"));
    Files.writeString(
        secrets.resolve("ZS_IDENTITY_CHALLENGE_PEPPER"),
        environment.get("ZS_IDENTITY_CHALLENGE_PEPPER"));
    Files.writeString(
        secrets.resolve("ZS_IDENTITY_MFA_ENCRYPTION_KEY"),
        environment.get("ZS_IDENTITY_MFA_ENCRYPTION_KEY"));

    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(ZellaStayApplication.class)
            .web(WebApplicationType.NONE)
            .properties(
                "ZS_SECRETS_DIR=" + secrets + "/",
                "ZS_ENVIRONMENT=" + environment.get("ZS_ENVIRONMENT"),
                "ZS_DB_URL=" + environment.get("ZS_DB_URL"),
                "ZS_DB_USER=" + environment.get("ZS_DB_USER"),
                "ZS_REDIS_HOST=" + environment.get("ZS_REDIS_HOST"),
                "ZS_REDIS_PORT=" + environment.get("ZS_REDIS_PORT"),
                "ZS_IDENTITY_TOKEN_SIGNING_KEY_ID="
                    + environment.get("ZS_IDENTITY_TOKEN_SIGNING_KEY_ID"),
                "ZS_IDENTITY_MFA_ENCRYPTION_KEY_ID="
                    + environment.get("ZS_IDENTITY_MFA_ENCRYPTION_KEY_ID"))
            .run()) {
      Integer one = context.getBean(JdbcTemplate.class).queryForObject("SELECT 1", Integer.class);
      context.getBean(StringRedisTemplate.class).opsForValue().set("phase0:secrets", "ok");

      assertThat(one).isEqualTo(1);
      IdentityProperties identity = context.getBean(IdentityProperties.class);
      assertThat(identity.tokens().signingKey())
          .isEqualTo(environment.get("ZS_IDENTITY_TOKEN_SIGNING_KEY"));
      assertThat(identity.challenges().pepper())
          .isEqualTo(environment.get("ZS_IDENTITY_CHALLENGE_PEPPER"));
    }
  }

  @Test
  void aMissingIdentitySecretFailsStartupNamingTheVariable(@TempDir Path secrets) {
    Map<String, String> environment = TestInfrastructure.applicationEnvironment();

    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(ZellaStayApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties(
                        "ZS_SECRETS_DIR=" + secrets + "/",
                        "ZS_ENVIRONMENT=" + environment.get("ZS_ENVIRONMENT"),
                        "ZS_DB_URL=" + environment.get("ZS_DB_URL"),
                        "ZS_DB_USER=" + environment.get("ZS_DB_USER"),
                        "ZS_DB_PASSWORD=" + environment.get("ZS_DB_PASSWORD"),
                        "ZS_REDIS_HOST=" + environment.get("ZS_REDIS_HOST"),
                        "ZS_IDENTITY_TOKEN_SIGNING_KEY_ID=k1")
                    .run())
        .hasStackTraceContaining("ZS_IDENTITY_");
  }
}
