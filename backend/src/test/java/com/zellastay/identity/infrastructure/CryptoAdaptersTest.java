package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CryptoAdaptersTest {

  private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(8192, 1, 1);
  private final DefaultSecretHasher secrets = new DefaultSecretHasher("pepper-one");
  private final SecureSecretGenerator generator = new SecureSecretGenerator();

  @Test
  void passwordsHashToArgon2idAndVerify() {
    String hash = hasher.hash("correct horse battery");

    assertThat(hash).startsWith("$argon2id$");
    assertThat(hasher.matches("correct horse battery", hash)).isTrue();
    assertThat(hasher.matches("wrong password!", hash)).isFalse();
  }

  @Test
  void hashesCarryARandomSalt() {
    assertThat(hasher.hash("same password 1")).isNotEqualTo(hasher.hash("same password 1"));
  }

  @Test
  void weakerStoredParametersAreReportedForRehash() {
    String old = new Argon2PasswordHasher(4096, 1, 1).hash("some password 12");

    assertThat(hasher.needsRehash(old)).isTrue();
    assertThat(hasher.needsRehash(hasher.hash("some password 12"))).isFalse();
  }

  @Test
  void keyedHashesDependOnThePepper() {
    String hash = secrets.keyed("123456");

    assertThat(hash).isEqualTo(secrets.keyed("123456")).hasSize(64);
    assertThat(new DefaultSecretHasher("pepper-two").keyed("123456")).isNotEqualTo(hash);
  }

  @Test
  void digestsAreStableAndDistinct() {
    assertThat(secrets.digest("token-a")).isEqualTo(secrets.digest("token-a")).hasSize(64);
    assertThat(secrets.digest("token-a")).isNotEqualTo(secrets.digest("token-b"));
  }

  @Test
  void constantTimeComparisonAcceptsEqualAndRejectsDifferentValues() {
    assertThat(secrets.sameSecret("abc", "abc")).isTrue();
    assertThat(secrets.sameSecret("abc", "abd")).isFalse();
    assertThat(secrets.sameSecret("abc", "abcd")).isFalse();
  }

  @Test
  void numericCodesAreSixDigitsAndTokensAreLongUniqueAndUrlSafe() {
    Set<String> tokens = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      assertThat(generator.numericCode()).matches("\\d{6}");
      tokens.add(generator.token());
    }

    assertThat(tokens).hasSize(200);
    assertThat(tokens).allSatisfy(token -> assertThat(token).matches("[A-Za-z0-9_-]{43}"));
  }
}
