package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.SecretHasher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class DefaultSecretHasher implements SecretHasher {

  private static final String HMAC = "HmacSHA256";

  private final byte[] pepper;

  public DefaultSecretHasher(String pepper) {
    this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String keyed(String secret) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(pepper, HMAC));
      return HexFormat.of().formatHex(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
    }
  }

  @Override
  public String digest(String secret) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  @Override
  public boolean sameSecret(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }
}
