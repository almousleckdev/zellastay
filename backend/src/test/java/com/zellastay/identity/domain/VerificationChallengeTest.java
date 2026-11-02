package com.zellastay.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.ResendTooSoonException;
import com.zellastay.identity.domain.VerificationChallenge.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationChallengeTest {

  private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");

  private static VerificationChallenge issued() {
    return VerificationChallenge.issue(
        UUID.randomUUID(),
        AccountId.random(),
        ChallengePurpose.EMAIL_VERIFICATION,
        "hash",
        T0,
        Duration.ofMinutes(10));
  }

  @Test
  void aMatchingCodeVerifiesOnceOnly() {
    VerificationChallenge challenge = issued();

    assertThat(challenge.check(true, T0.plusSeconds(30), 5)).isEqualTo(Result.VERIFIED);
    assertThat(challenge.check(true, T0.plusSeconds(31), 5)).isEqualTo(Result.USED);
  }

  @Test
  void aWrongCodeCountsAnAttemptAndTheLastAllowedAttemptBurnsTheChallenge() {
    VerificationChallenge challenge = issued();

    for (int i = 0; i < 4; i++) {
      assertThat(challenge.check(false, T0, 5)).isEqualTo(Result.WRONG);
    }
    assertThat(challenge.check(false, T0, 5)).isEqualTo(Result.BURNED);
    assertThat(challenge.check(true, T0, 5)).isEqualTo(Result.BURNED);
  }

  @Test
  void anExpiredChallengeNeverVerifies() {
    VerificationChallenge challenge = issued();

    assertThat(challenge.check(true, T0.plus(Duration.ofMinutes(10)), 5)).isEqualTo(Result.EXPIRED);
  }

  @Test
  void resendIsRefusedInsideTheCooldown() {
    assertThatThrownBy(
            () ->
                ResendPolicy.check(
                    Optional.of(T0), 1, T0.plusSeconds(30), Duration.ofSeconds(60), 5))
        .isInstanceOf(ResendTooSoonException.class);
  }

  @Test
  void resendIsRefusedAtTheHourlyCapAndAllowedOtherwise() {
    assertThatThrownBy(
            () ->
                ResendPolicy.check(
                    Optional.of(T0), 5, T0.plusSeconds(90), Duration.ofSeconds(60), 5))
        .isInstanceOf(ResendTooSoonException.class);
    assertThatCode(
            () ->
                ResendPolicy.check(
                    Optional.of(T0), 4, T0.plusSeconds(90), Duration.ofSeconds(60), 5))
        .doesNotThrowAnyException();
    assertThatCode(() -> ResendPolicy.check(Optional.empty(), 0, T0, Duration.ofSeconds(60), 5))
        .doesNotThrowAnyException();
  }
}
