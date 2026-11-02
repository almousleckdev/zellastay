package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.domain.AccessTokens.IssuedToken;
import com.zellastay.identity.domain.AccessTokens.TokenClaims;
import com.zellastay.shared.security.TokenAudience;
import com.zellastay.support.TestKeys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EcAccessTokensTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
  private static final Duration TTL = Duration.ofMinutes(15);

  private final TestKeys.Pair keys = TestKeys.generate();
  private final EcAccessTokens tokens =
      new EcAccessTokens("zellastay-test", "k1", keys.privatePem(), Map.of(), TTL);

  private IssuedToken issue(TokenAudience audience, boolean mfa) {
    return tokens.issue(AccountId.random(), audience, UUID.randomUUID(), mfa, NOW);
  }

  @Test
  void issuedTokensCarryTheDocumentedClaims() {
    AccountId account = AccountId.random();
    UUID session = UUID.randomUUID();
    IssuedToken token = tokens.issue(account, TokenAudience.VENDOR, session, true, NOW);

    TokenClaims claims =
        tokens.parse(token.value(), TokenAudience.VENDOR, NOW.plusSeconds(60)).orElseThrow();

    assertThat(claims.accountId()).isEqualTo(account);
    assertThat(claims.sessionId()).isEqualTo(session);
    assertThat(claims.audience()).isEqualTo(TokenAudience.VENDOR);
    assertThat(claims.mfaVerified()).isTrue();
    assertThat(claims.expiresAt()).isEqualTo(NOW.plus(TTL));
    assertThat(token.expiresAt()).isEqualTo(NOW.plus(TTL));
    String payload = decodePayload(token.value());
    assertThat(payload)
        .contains("\"iss\":\"zellastay-test\"")
        .contains("\"aud\":\"zs-vendor\"")
        .contains("\"jti\"");
    assertThat(payload).doesNotContain("organization").doesNotContain("permission");
  }

  @Test
  void expiredTokensAreRejected() {
    IssuedToken token = issue(TokenAudience.CUSTOMER, false);

    assertThat(tokens.parse(token.value(), TokenAudience.CUSTOMER, NOW.plus(TTL))).isEmpty();
    assertThat(tokens.parse(token.value(), TokenAudience.CUSTOMER, NOW.plus(TTL).minusSeconds(1)))
        .isPresent();
  }

  @Test
  void aTokenForAnotherAudienceIsRejected() {
    IssuedToken token = issue(TokenAudience.CUSTOMER, false);

    assertThat(tokens.parse(token.value(), TokenAudience.ADMIN, NOW.plusSeconds(1))).isEmpty();
    assertThat(tokens.parse(token.value(), TokenAudience.VENDOR, NOW.plusSeconds(1))).isEmpty();
  }

  @Test
  void tokensSignedByAnotherKeyUnderTheSameKeyIdAreRejected() {
    EcAccessTokens attacker =
        new EcAccessTokens("zellastay-test", "k1", TestKeys.generate().privatePem(), Map.of(), TTL);
    IssuedToken forged =
        attacker.issue(AccountId.random(), TokenAudience.ADMIN, UUID.randomUUID(), true, NOW);

    assertThat(tokens.parse(forged.value(), TokenAudience.ADMIN, NOW.plusSeconds(1))).isEmpty();
  }

  @Test
  void tokensFromAnotherIssuerAreRejected() {
    EcAccessTokens other =
        new EcAccessTokens("someone-else", "k1", keys.privatePem(), Map.of(), TTL);
    IssuedToken token =
        other.issue(AccountId.random(), TokenAudience.CUSTOMER, UUID.randomUUID(), false, NOW);

    assertThat(tokens.parse(token.value(), TokenAudience.CUSTOMER, NOW.plusSeconds(1))).isEmpty();
  }

  @Test
  void unsignedAndTamperedAndMalformedTokensAreRejected() {
    IssuedToken token = issue(TokenAudience.CUSTOMER, false);
    String[] parts = token.value().split("\\.");
    String unsigned = b64("{\"alg\":\"none\",\"kid\":\"k1\"}") + "." + parts[1] + ".";
    String tamperedPayload =
        parts[0]
            + "."
            + b64(decodePayload(token.value()).replace("zs-customer", "zs-admin"))
            + "."
            + parts[2];

    assertThat(tokens.parse(unsigned, TokenAudience.CUSTOMER, NOW.plusSeconds(1))).isEmpty();
    assertThat(tokens.parse(tamperedPayload, TokenAudience.ADMIN, NOW.plusSeconds(1))).isEmpty();
    assertThat(tokens.parse("not-a-jwt", TokenAudience.CUSTOMER, NOW)).isEmpty();
    assertThat(tokens.parse("", TokenAudience.CUSTOMER, NOW)).isEmpty();
    assertThat(tokens.parse(null, TokenAudience.CUSTOMER, NOW)).isEmpty();
  }

  @Test
  void rotationKeepsOldTokensValidUntilTheOldKeyIsRetired() {
    IssuedToken signedByOldKey = issue(TokenAudience.CUSTOMER, false);
    TestKeys.Pair next = TestKeys.generate();
    EcAccessTokens rotated =
        new EcAccessTokens(
            "zellastay-test", "k2", next.privatePem(), Map.of("k1", keys.publicPem()), TTL);
    EcAccessTokens retired =
        new EcAccessTokens("zellastay-test", "k2", next.privatePem(), Map.of(), TTL);
    IssuedToken signedByNewKey =
        rotated.issue(AccountId.random(), TokenAudience.CUSTOMER, UUID.randomUUID(), false, NOW);

    Optional<TokenClaims> oldAccepted =
        rotated.parse(signedByOldKey.value(), TokenAudience.CUSTOMER, NOW.plusSeconds(1));

    assertThat(oldAccepted).isPresent();
    assertThat(rotated.parse(signedByNewKey.value(), TokenAudience.CUSTOMER, NOW.plusSeconds(1)))
        .isPresent();
    assertThat(retired.parse(signedByOldKey.value(), TokenAudience.CUSTOMER, NOW.plusSeconds(1)))
        .isEmpty();
  }

  private static String decodePayload(String token) {
    return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
  }

  private static String b64(String text) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }
}
