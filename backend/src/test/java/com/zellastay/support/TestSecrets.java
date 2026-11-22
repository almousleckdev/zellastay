package com.zellastay.support;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** Throwaway secrets generated once per test run; no secret is ever written down. */
public final class TestSecrets {

  private static final String REDIS_PASSWORD = UUID.randomUUID().toString();
  private static final String CHALLENGE_PEPPER = UUID.randomUUID().toString();
  private static final String TOKEN_SIGNING_KEY = TestKeys.generate().privatePem();
  private static final String MFA_ENCRYPTION_KEY = randomAesKey();

  private TestSecrets() {}

  public static String redisPassword() {
    return REDIS_PASSWORD;
  }

  public static String challengePepper() {
    return CHALLENGE_PEPPER;
  }

  public static String tokenSigningKey() {
    return TOKEN_SIGNING_KEY;
  }

  public static String mfaEncryptionKey() {
    return MFA_ENCRYPTION_KEY;
  }

  private static String randomAesKey() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
