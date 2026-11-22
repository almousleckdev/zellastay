package com.zellastay.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The PostgreSQL and Redis containers shared by every integration test of one run. */
public final class TestContainers {

  private static final int REDIS_PORT = 6379;

  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine")
          .withCommand("redis-server", "--requirepass", TestSecrets.redisPassword())
          .withExposedPorts(REDIS_PORT);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  private TestContainers() {}

  public static String jdbcUrl() {
    return POSTGRES.getJdbcUrl();
  }

  public static String databaseUser() {
    return POSTGRES.getUsername();
  }

  public static String databasePassword() {
    return POSTGRES.getPassword();
  }

  public static String redisHost() {
    return REDIS.getHost();
  }

  public static int redisPort() {
    return REDIS.getMappedPort(REDIS_PORT);
  }
}
