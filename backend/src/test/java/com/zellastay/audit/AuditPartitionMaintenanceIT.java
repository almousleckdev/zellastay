package com.zellastay.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.audit.infrastructure.AuditPartitionMaintainer;
import com.zellastay.audit.infrastructure.AuditPartitionMaintainer.Report;
import com.zellastay.support.IntegrationTest;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class AuditPartitionMaintenanceIT {

  @Autowired AuditPartitionMaintainer maintainer;
  @Autowired JdbcTemplate jdbc;
  @Autowired Clock clock;

  private boolean partitionExists(YearMonth month) {
    String name = "audit_event_y%04dm%02d".formatted(month.getYear(), month.getMonthValue());
    return jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, name);
  }

  @Test
  void futurePartitionsExistAheadOfTheCurrentMonthAndTheJobIsIdempotent() {
    YearMonth now = YearMonth.now(clock.withZone(ZoneOffset.UTC));

    maintainer.ensurePartitions();
    Report second = maintainer.ensurePartitions();

    assertThat(partitionExists(now)).isTrue();
    assertThat(partitionExists(now.plusMonths(1))).isTrue();
    assertThat(partitionExists(now.plusMonths(3))).isTrue();
    assertThat(second.created()).isZero();
  }

  @Test
  void eventsOutsideEveryPartitionLandInTheDefaultPartitionAndAreReported() {
    String subject = UUID.randomUUID().toString();
    jdbc.update(
        "insert into audit_event (id, occurred_at, actor_type, action, subject_type, subject_id, outcome)"
            + " values (gen_random_uuid(), timestamptz '2099-01-15 00:00:00+00', 'SYSTEM',"
            + " 'test.straggler', 'account', ?, 'SUCCESS')",
        subject);

    Report report = maintainer.ensurePartitions();

    assertThat(report.stragglers()).isGreaterThanOrEqualTo(1);
  }
}
