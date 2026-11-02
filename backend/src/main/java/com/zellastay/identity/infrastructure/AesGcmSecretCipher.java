package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM for secrets stored at rest. Format: {@code keyId.iv.ciphertext} (Base64). */
public class AesGcmSecretCipher implements SecretCipher {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int KEY_BYTES = 32;

  private final String keyId;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public AesGcmSecretCipher(String keyId, String base64Key) {
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64Key.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("ZS_IDENTITY_MFA_ENCRYPTION_KEY is not valid Base64", e);
    }
    if (bytes.length != KEY_BYTES) {
      throw new IllegalStateException("ZS_IDENTITY_MFA_ENCRYPTION_KEY must be 32 bytes");
    }
    this.keyId = keyId;
    this.key = new SecretKeySpec(bytes, "AES");
  }

  @Override
  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      Base64.Encoder encoder = Base64.getEncoder();
      return keyId + "." + encoder.encodeToString(iv) + "." + encoder.encodeToString(encrypted);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("encryption failed", e);
    }
  }

  @Override
  public String decrypt(String stored) {
    try {
      String[] parts = stored.split("\\.");
      if (parts.length != 3 || !parts[0].equals(keyId)) {
        throw new IllegalStateException("unknown ciphertext format or key");
      }
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(parts[1])));
      cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("decryption failed", e);
    }
  }
}
