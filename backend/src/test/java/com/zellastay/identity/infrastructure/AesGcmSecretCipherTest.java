package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmSecretCipherTest {

  private static String key() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }

  @Test
  void roundTripsAndNeverRepeatsCiphertext() {
    AesGcmSecretCipher cipher = new AesGcmSecretCipher("k1", key());

    String first = cipher.encrypt("JBSWY3DPEHPK3PXP");
    String second = cipher.encrypt("JBSWY3DPEHPK3PXP");

    assertThat(cipher.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
    assertThat(first).isNotEqualTo(second).doesNotContain("JBSWY3DPEHPK3PXP");
  }

  @Test
  void aDifferentKeyOrTamperedCiphertextFailsToDecrypt() {
    AesGcmSecretCipher cipher = new AesGcmSecretCipher("k1", key());
    String encrypted = cipher.encrypt("secret");
    String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

    assertThatThrownBy(() -> new AesGcmSecretCipher("k1", key()).decrypt(encrypted))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsKeysThatAreNotThirtyTwoBytes() {
    assertThatThrownBy(
            () -> new AesGcmSecretCipher("k1", Base64.getEncoder().encodeToString(new byte[16])))
        .isInstanceOf(IllegalStateException.class);
  }
}
