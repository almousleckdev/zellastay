package com.zellastay.audit.infrastructure;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Creates upcoming monthly partitions and reports events that fell outside every partition. */
@Component
public class AuditPartitionMaintainer {

  private static final Logger LOG = LoggerFactory.getLogger(AuditPartitionMaintainer.class);
  private static final int STRAGGLER_SCAN_LIMIT = 1000;

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final AuditProperties properties;

  AuditPartitionMaintainer(JdbcTemplate jdbc, Clock clock, AuditProperties properties) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.properties = properties;
  }

  public record Report(int created, int stragglers) {}

  @Scheduled(cron = "${zellastay.audit.maintenance-cron}", zone = "UTC")
  void scheduled() {
    ensurePartitions();
  }

  public Report ensurePartitions() {
    LocalDate first = LocalDate.now(clock.withZone(ZoneOffset.UTC)).withDayOfMonth(1);
    int created = 0;
    for (int offset = 0; offset <= properties.partitionsAhead(); offset++) {
      Boolean made =
          jdbc.queryForObject(
              "select audit_ensure_partition(?)",
              Boolean.class,
              java.sql.Date.valueOf(first.plusMonths(offset)));
      if (Boolean.TRUE.equals(made)) {
        created++;
      }
    }
    Integer stragglers =
        jdbc.queryForObject(
            "select count(*) from (select 1 from audit_event_default limit ?) s",
            Integer.class,
            STRAGGLER_SCAN_LIMIT);
    int stragglerCount = stragglers == null ? 0 : stragglers;
    if (stragglerCount > 0) {
      LOG.warn("{} audit events are in the default partition and need attention", stragglerCount);
    }
    return new Report(created, stragglerCount);
  }
}
