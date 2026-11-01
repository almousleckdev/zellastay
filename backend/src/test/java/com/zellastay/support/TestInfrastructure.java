package com.zellastay.support;

import java.util.Map;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class TestInfrastructure {

  private static final String REDIS_PASSWORD = UUID.randomUUID().toString();

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
    return Map.of(
        "ZS_ENVIRONMENT", "CI",
        "ZS_DB_URL", POSTGRES.getJdbcUrl(),
        "ZS_DB_USER", POSTGRES.getUsername(),
        "ZS_DB_PASSWORD", POSTGRES.getPassword(),
        "ZS_REDIS_HOST", REDIS.getHost(),
        "ZS_REDIS_PORT", String.valueOf(REDIS.getMappedPort(6379)),
        "ZS_REDIS_PASSWORD", REDIS_PASSWORD,
        "ZS_SERVER_PORT", "0",
        "ZS_MANAGEMENT_PORT", "0");
  }
}
