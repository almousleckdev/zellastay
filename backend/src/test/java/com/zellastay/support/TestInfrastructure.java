package com.zellastay.support;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class TestInfrastructure {

  private static final String REDIS_PASSWORD = UUID.randomUUID().toString();

  private static final String IDENTITY_PEPPER = UUID.randomUUID().toString();

  private static final String IDENTITY_SIGNING_KEY = generateSigningKey();

  private static final String MFA_KEY = randomAesKey();

  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine")
          .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
          .withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  private TestInfrastructure() {}

  public static Map<String, String> applicationEnvironment() {
    return Map.ofEntries(
        Map.entry("ZS_ENVIRONMENT", "CI"),
        Map.entry("ZS_DB_URL", POSTGRES.getJdbcUrl()),
        Map.entry("ZS_DB_USER", POSTGRES.getUsername()),
        Map.entry("ZS_DB_PASSWORD", POSTGRES.getPassword()),
        Map.entry("ZS_REDIS_HOST", REDIS.getHost()),
        Map.entry("ZS_REDIS_PORT", String.valueOf(REDIS.getMappedPort(6379))),
        Map.entry("ZS_REDIS_PASSWORD", REDIS_PASSWORD),
        Map.entry("ZS_SERVER_PORT", "0"),
        Map.entry("ZS_MANAGEMENT_PORT", "0"),
        Map.entry("ZS_IDENTITY_TOKEN_SIGNING_KEY_ID", "test-key"),
        Map.entry("ZS_IDENTITY_TOKEN_SIGNING_KEY", IDENTITY_SIGNING_KEY),
        Map.entry("ZS_IDENTITY_CHALLENGE_PEPPER", IDENTITY_PEPPER),
        Map.entry("ZS_IDENTITY_MFA_ENCRYPTION_KEY_ID", "test-mfa-key"),
        Map.entry("ZS_IDENTITY_MFA_ENCRYPTION_KEY", MFA_KEY));
  }

  private static String randomAesKey() {
    byte[] bytes = new byte[32];
    new java.security.SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static String generateSigningKey() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
      String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
      return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot generate a test signing key", e);
    }
  }
}
