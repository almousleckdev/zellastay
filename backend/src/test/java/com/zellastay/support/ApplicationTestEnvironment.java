package com.zellastay.support;

import java.util.Map;

/** The environment variables a test application instance is started with. */
public final class ApplicationTestEnvironment {

  private ApplicationTestEnvironment() {}

  public static Map<String, String> variables() {
    return Map.ofEntries(
        Map.entry("ZS_ENVIRONMENT", "CI"),
        Map.entry("ZS_DB_URL", TestContainers.jdbcUrl()),
        Map.entry("ZS_DB_USER", TestContainers.databaseUser()),
        Map.entry("ZS_DB_PASSWORD", TestContainers.databasePassword()),
        Map.entry("ZS_REDIS_HOST", TestContainers.redisHost()),
        Map.entry("ZS_REDIS_PORT", String.valueOf(TestContainers.redisPort())),
        Map.entry("ZS_REDIS_PASSWORD", TestSecrets.redisPassword()),
        Map.entry("ZS_SERVER_PORT", "0"),
        Map.entry("ZS_MANAGEMENT_PORT", "0"),
        Map.entry("ZS_IDENTITY_TOKEN_SIGNING_KEY_ID", "test-key"),
        Map.entry("ZS_IDENTITY_TOKEN_SIGNING_KEY", TestSecrets.tokenSigningKey()),
        Map.entry("ZS_IDENTITY_CHALLENGE_PEPPER", TestSecrets.challengePepper()),
        Map.entry("ZS_IDENTITY_MFA_ENCRYPTION_KEY_ID", "test-mfa-key"),
        Map.entry("ZS_IDENTITY_MFA_ENCRYPTION_KEY", TestSecrets.mfaEncryptionKey()));
  }
}
