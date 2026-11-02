package com.zellastay.identity.domain;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 time-based one-time passwords: HMAC-SHA-1, 30-second steps, six digits. */
public final class Totp {

  private static final int STEP_SECONDS = 30;
  private static final int DIGITS = 6;
  private static final int SECRET_BYTES = 20;
  private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};

  private Totp() {}

  public static byte[] newSecret() {
    byte[] secret = new byte[SECRET_BYTES];
    new SecureRandom().nextBytes(secret);
    return secret;
  }

  public static String code(byte[] secret, long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
      int offset = hash[hash.length - 1] & 0x0F;
      int binary = ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & 0x7FFFFFFF;
      int otp = binary % POWERS[DIGITS];
      return ("%0" + DIGITS + "d").formatted(otp);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA-1 is unavailable", e);
    }
  }

  /**
   * Returns the matching step within one step either side of {@code now}, if it is newer than the
   * last step already used (which prevents replaying a code).
   */
  public static OptionalLong verify(byte[] secret, String code, Instant now, long lastUsedStep) {
    if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
      return OptionalLong.empty();
    }
    long current = now.getEpochSecond() / STEP_SECONDS;
    for (long step = current - 1; step <= current + 1; step++) {
      boolean equal =
          MessageDigest.isEqual(
              code(secret, step).getBytes(StandardCharsets.US_ASCII),
              code.getBytes(StandardCharsets.US_ASCII));
      if (equal && step > lastUsedStep) {
        return OptionalLong.of(step);
      }
    }
    return OptionalLong.empty();
  }

  public static String provisioningUri(String issuer, String account, byte[] secret) {
    String label = encode(issuer) + ":" + encode(account);
    return "otpauth://totp/"
        + label
        + "?secret="
        + Base32.encode(secret)
        + "&issuer="
        + encode(issuer)
        + "&algorithm=SHA1&digits="
        + DIGITS
        + "&period="
        + STEP_SECONDS;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
