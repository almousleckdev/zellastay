package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.shared.security.TokenAudience;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IdentityPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(Enabled.class);

  @EnableConfigurationProperties(IdentityProperties.class)
  static class Enabled {}

  private static List<String> valid() {
    List<String> values = new ArrayList<>();
    values.add("zellastay.identity.tokens.issuer=zellastay-test");
    values.add("zellastay.identity.tokens.signing-key-id=k1");
    values.add("zellastay.identity.tokens.signing-key=pem-placeholder");
    values.add("zellastay.identity.tokens.access-ttl=15m");
    values.add("zellastay.identity.tokens.refresh-reuse-grace=10s");
    values.add("zellastay.identity.tokens.session-cache-ttl=30s");
    values.add("zellastay.identity.tokens.sessions.customer.absolute=30d");
    values.add("zellastay.identity.tokens.sessions.customer.idle=14d");
    values.add("zellastay.identity.tokens.sessions.vendor.absolute=14d");
    values.add("zellastay.identity.tokens.sessions.vendor.idle=3d");
    values.add("zellastay.identity.tokens.sessions.admin.absolute=12h");
    values.add("zellastay.identity.tokens.sessions.admin.idle=30m");
    values.add("zellastay.identity.lockout.threshold=5");
    values.add("zellastay.identity.lockout.base-duration=1m");
    values.add("zellastay.identity.lockout.max-duration=1h");
    values.add("zellastay.identity.mfa.encryption-key-id=k");
    values.add("zellastay.identity.mfa.encryption-key=key");
    values.add("zellastay.identity.mfa.ticket-ttl=5m");
    values.add("zellastay.identity.mfa.max-attempts=5");
    values.add("zellastay.identity.mfa.issuer-label=Zella Stay");
    values.add("zellastay.identity.bootstrap.enabled=false");
    values.add("zellastay.identity.hashing.memory-kib=8192");
    values.add("zellastay.identity.hashing.iterations=1");
    values.add("zellastay.identity.hashing.parallelism=1");
    values.add("zellastay.identity.challenges.code-ttl=10m");
    values.add("zellastay.identity.challenges.max-attempts=5");
    values.add("zellastay.identity.challenges.resend-cooldown=60s");
    values.add("zellastay.identity.challenges.max-sends-per-hour=5");
    values.add("zellastay.identity.challenges.reset-token-ttl=30m");
    values.add("zellastay.identity.challenges.pepper=pepper-placeholder");
    return values;
  }

  private ApplicationContextRunner with(List<String> values) {
    return runner.withPropertyValues(values.toArray(String[]::new));
  }

  private static List<String> without(String prefix) {
    return valid().stream().filter(v -> !v.startsWith(prefix)).toList();
  }

  private static List<String> replacing(String prefix, String value) {
    List<String> values = new ArrayList<>(without(prefix));
    values.add(prefix + "=" + value);
    return values;
  }

  @Test
  void bindsAValidConfiguration() {
    with(valid())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              IdentityProperties properties = context.getBean(IdentityProperties.class);
              assertThat(properties.tokens().accessTtl()).isEqualTo(Duration.ofMinutes(15));
              assertThat(properties.tokens().sessions().get(TokenAudience.ADMIN).idle())
                  .isEqualTo(Duration.ofMinutes(30));
              assertThat(properties.lockout().threshold()).isEqualTo(5);
            });
  }

  @Test
  void aMissingSigningKeyFailsStartupNamingTheSetting() {
    with(without("zellastay.identity.tokens.signing-key="))
        .run(
            context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("signingKey"));
  }

  @Test
  void aMissingPepperFailsStartup() {
    with(without("zellastay.identity.challenges.pepper"))
        .run(
            context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("pepper"));
  }

  @Test
  void accessTokensLongerThanFifteenMinutesAreRejected() {
    with(replacing("zellastay.identity.tokens.access-ttl", "16m"))
        .run(
            context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("access-ttl"));
  }

  @Test
  void theSessionCacheMayNotOutliveSixtySeconds() {
    with(replacing("zellastay.identity.tokens.session-cache-ttl", "90s"))
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("session-cache-ttl"));
  }

  @Test
  void everyAudienceNeedsSessionLifetimes() {
    List<String> noAdmin = valid().stream().filter(v -> !v.contains("sessions.admin")).toList();

    with(noAdmin)
        .run(
            context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("sessions"));
  }

  @Test
  void bootstrapNeedsCredentialsWhenEnabled() {
    with(replacing("zellastay.identity.bootstrap.enabled", "true"))
        .run(
            context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("bootstrap"));
  }

  @Test
  void idleTimeoutCannotExceedTheAbsoluteLifetime() {
    with(replacing("zellastay.identity.tokens.sessions.admin.idle", "13h"))
        .run(
            context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("idle"));
  }

  @Test
  void lockoutMaximumCannotBeBelowTheBaseDuration() {
    with(replacing("zellastay.identity.lockout.max-duration", "30s"))
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("max-duration"));
  }
}
