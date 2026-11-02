package com.zellastay.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

  private static final byte[] RFC_SECRET =
      "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  /** RFC 6238 appendix B (SHA-1), truncated to the last six digits of the published values. */
  @ParameterizedTest
  @CsvSource({
    "59,287082",
    "1111111109,081804",
    "1111111111,050471",
    "1234567890,005924",
    "2000000000,279037",
    "20000000000,353130"
  })
  void matchesTheRfcTestVectors(long epochSecond, String expected) {
    assertThat(Totp.code(RFC_SECRET, epochSecond / 30)).isEqualTo(expected);
  }

  @Test
  void acceptsTheCurrentAndAdjacentStepsOnly() {
    Instant now = Instant.ofEpochSecond(1_234_567_890L);
    long step = now.getEpochSecond() / 30;

    assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, 0)).hasValue(step);
    assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 0)).hasValue(step - 1);
    assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, 0)).hasValue(step + 1);
    assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, 0)).isEmpty();
    assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, 0)).isEmpty();
  }

  @Test
  void aStepThatWasAlreadyUsedCannotBeReplayed() {
    Instant now = Instant.ofEpochSecond(1_234_567_890L);
    long step = now.getEpochSecond() / 30;
    String code = Totp.code(RFC_SECRET, step);

    OptionalLong first = Totp.verify(RFC_SECRET, code, now, 0);
    OptionalLong replay = Totp.verify(RFC_SECRET, code, now, first.getAsLong());

    assertThat(first).isPresent();
    assertThat(replay).isEmpty();
  }

  @Test
  void rejectsMalformedCodes() {
    Instant now = Instant.ofEpochSecond(59);

    assertThat(Totp.verify(RFC_SECRET, "12345", now, 0)).isEmpty();
    assertThat(Totp.verify(RFC_SECRET, "abcdef", now, 0)).isEmpty();
    assertThat(Totp.verify(RFC_SECRET, null, now, 0)).isEmpty();
  }

  @Test
  void base32RoundTripsAndMatchesTheStandardAlphabet() {
    byte[] data = "Hello!".getBytes(StandardCharsets.US_ASCII);

    assertThat(Base32.encode(data)).isEqualTo("JBSWY3DPEE");
    assertThat(Base32.decode("JBSWY3DPEE")).isEqualTo(data);
    assertThat(Base32.decode(Base32.encode(RFC_SECRET))).isEqualTo(RFC_SECRET);
  }

  @Test
  void generatedSecretsAreTwentyRandomBytes() {
    assertThat(Totp.newSecret()).hasSize(20).isNotEqualTo(Totp.newSecret());
  }

  @Test
  void theProvisioningUriCarriesIssuerAccountAndSecret() {
    String uri = Totp.provisioningUri("Zella Stay", "a@example.com", RFC_SECRET);

    assertThat(uri)
        .startsWith("otpauth://totp/Zella%20Stay:a%40example.com?")
        .contains("secret=" + Base32.encode(RFC_SECRET))
        .contains("issuer=Zella%20Stay")
        .contains("digits=6")
        .contains("period=30");
  }
}
