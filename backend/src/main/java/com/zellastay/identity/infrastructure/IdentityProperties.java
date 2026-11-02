package com.zellastay.identity.infrastructure;

import com.zellastay.shared.security.TokenAudience;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed identity configuration. Secrets come only from the environment or mounted secret files. */
@Validated
@ConfigurationProperties(prefix = "zellastay.identity")
public record IdentityProperties(
    @NotNull @Valid Tokens tokens,
    @NotNull @Valid Lockout lockout,
    @NotNull @Valid Hashing hashing,
    @NotNull @Valid Mfa mfa,
    @NotNull @Valid Bootstrap bootstrap,
    @NotNull @Valid Challenges challenges) {

  static final Duration MAX_ACCESS_TTL = Duration.ofMinutes(15);

  public record Tokens(
      @NotBlank String issuer,
      @NotBlank(message = "ZS_IDENTITY_TOKEN_SIGNING_KEY_ID is required") String signingKeyId,
      @NotBlank(message = "ZS_IDENTITY_TOKEN_SIGNING_KEY is required") String signingKey,
      @NotNull Duration accessTtl,
      @NotNull Duration refreshReuseGrace,
      @NotNull Duration sessionCacheTtl,
      Map<String, String> retiredPublicKeys,
      @NotNull Map<TokenAudience, @Valid SessionLifetime> sessions) {

    @AssertTrue(message = "access-ttl must be positive and at most 15 minutes")
    boolean isAccessTtlWithinLimit() {
      return accessTtl == null
          || (accessTtl.isPositive() && accessTtl.compareTo(MAX_ACCESS_TTL) <= 0);
    }

    @AssertTrue(message = "session-cache-ttl must be positive and at most 60 seconds")
    boolean isSessionCacheTtlWithinLimit() {
      return sessionCacheTtl == null
          || (sessionCacheTtl.isPositive()
              && sessionCacheTtl.compareTo(Duration.ofSeconds(60)) <= 0);
    }

    @AssertTrue(message = "sessions must define a lifetime for every audience")
    boolean isEveryAudienceConfigured() {
      return sessions == null || sessions.keySet().containsAll(List.of(TokenAudience.values()));
    }
  }

  public record SessionLifetime(@NotNull Duration absolute, @NotNull Duration idle) {

    @AssertTrue(message = "idle must be positive and must not exceed absolute")
    boolean isIdleWithinAbsolute() {
      return idle == null
          || absolute == null
          || (idle.isPositive() && idle.compareTo(absolute) <= 0);
    }
  }

  public record Lockout(
      @Min(1) int threshold, @NotNull Duration baseDuration, @NotNull Duration maxDuration) {

    @AssertTrue(message = "max-duration must not be shorter than base-duration")
    boolean isMaxAtLeastBase() {
      return baseDuration == null
          || maxDuration == null
          || maxDuration.compareTo(baseDuration) >= 0;
    }
  }

  public record Mfa(
      @NotBlank(message = "ZS_IDENTITY_MFA_ENCRYPTION_KEY_ID is required") String encryptionKeyId,
      @NotBlank(message = "ZS_IDENTITY_MFA_ENCRYPTION_KEY is required") String encryptionKey,
      @NotNull Duration ticketTtl,
      @Min(1) int maxAttempts,
      @NotBlank String issuerLabel) {}

  /** One-time creation of the first Super Admin; disabled unless explicitly switched on. */
  public record Bootstrap(boolean enabled, String email, String password) {

    @AssertTrue(message = "bootstrap needs an email and a password when enabled")
    boolean isCompleteWhenEnabled() {
      return !enabled
          || (email != null && !email.isBlank() && password != null && !password.isBlank());
    }

    @Override
    public String toString() {
      return "Bootstrap[enabled=" + enabled + "]";
    }
  }

  public record Hashing(
      @Min(1024) int memoryKib, @Min(1) int iterations, @Min(1) int parallelism) {}

  public record Challenges(
      @NotNull Duration codeTtl,
      @Min(1) int maxAttempts,
      @NotNull Duration resendCooldown,
      @Min(1) int maxSendsPerHour,
      @NotNull Duration resetTokenTtl,
      @NotBlank(message = "ZS_IDENTITY_CHALLENGE_PEPPER is required") String pepper) {}
}
