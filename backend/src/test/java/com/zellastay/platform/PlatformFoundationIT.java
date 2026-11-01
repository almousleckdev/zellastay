package com.zellastay.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.shared.RuntimeEnvironment;
import com.zellastay.shared.RuntimeProperties;
import com.zellastay.support.IntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PlatformFoundationIT {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private StringRedisTemplate redis;
  @Autowired private Clock clock;
  @Autowired private RuntimeProperties runtime;

  @Value("${local.management.port}")
  private int managementPort;

  @Test
  void flywayAppliesPlatformMigrationsAndCreatesEventPublicationTable() {
    Integer applied =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
    Integer outboxTable =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'event_publication'",
            Integer.class);

    assertThat(applied).isGreaterThanOrEqualTo(1);
    assertThat(outboxTable).isEqualTo(1);
  }

  @Test
  void redisIsReachableWithPassword() {
    redis.opsForValue().set("phase0:probe", "ok");

    assertThat(redis.opsForValue().get("phase0:probe")).isEqualTo("ok");
  }

  @Test
  void clockIsInjectedAndUtc() {
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void runtimePropertiesAreBoundFromEnvironment() {
    assertThat(runtime.environment()).isEqualTo(RuntimeEnvironment.CI);
  }

  @Test
  void livenessAndReadinessAreUpAndReadinessIgnoresRedis() throws Exception {
    HttpResponse<String> liveness = get("/actuator/health/liveness");
    HttpResponse<String> readiness = get("/actuator/health/readiness");

    assertThat(liveness.statusCode()).isEqualTo(200);
    assertThat(readiness.statusCode()).isEqualTo(200);
    assertThat(readiness.body()).doesNotContain("redis");
  }

  @Test
  void prometheusMetricsCarryApplicationAndEnvironmentTags() throws Exception {
    HttpResponse<String> metrics = get("/actuator/prometheus");

    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body()).contains("application=\"zellastay\"").contains("environment=\"CI\"");
  }

  @Test
  void unexposedActuatorEndpointsAreNotAvailable() throws Exception {
    assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
    assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path)).build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }
}
