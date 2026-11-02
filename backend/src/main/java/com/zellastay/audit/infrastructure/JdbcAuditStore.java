package com.zellastay.audit.infrastructure;

import com.zellastay.audit.api.ActorType;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.domain.AuditPage;
import com.zellastay.audit.domain.AuditQuery;
import com.zellastay.audit.domain.AuditRecord;
import com.zellastay.audit.domain.AuditStore;
import com.zellastay.audit.domain.InvalidAuditQueryException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcAuditStore implements AuditStore {

  private static final String INSERT =
      """
      insert into audit_event (id, occurred_at, actor_type, actor_id, actor_org_id, action,
        subject_type, subject_id, outcome, ip, user_agent, correlation_id, details)
      values (:id, :occurredAt, :actorType, :actorId, :actorOrgId, :action,
        :subjectType, :subjectId, :outcome, :ip, :userAgent, :correlationId, cast(:details as jsonb))
      """;

  private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

  private final NamedParameterJdbcTemplate jdbc;
  private final JsonMapper json;

  JdbcAuditStore(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void append(AuditRecord record) {
    jdbc.update(
        INSERT,
        new MapSqlParameterSource()
            .addValue("id", record.id())
            .addValue("occurredAt", Timestamp.from(record.occurredAt()))
            .addValue("actorType", record.actorType().name())
            .addValue("actorId", record.actorId())
            .addValue("actorOrgId", record.actorOrganizationId())
            .addValue("action", record.action())
            .addValue("subjectType", record.subjectType())
            .addValue("subjectId", record.subjectId())
            .addValue("outcome", record.outcome().name())
            .addValue("ip", record.ip())
            .addValue("userAgent", record.userAgent())
            .addValue("correlationId", record.correlationId())
            .addValue("details", json.writeValueAsString(record.details())));
  }

  @Override
  public AuditPage find(AuditQuery query) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    List<String> where = new ArrayList<>();
    filter(where, params, query);
    String sql =
        "select * from audit_event"
            + (where.isEmpty() ? "" : " where " + String.join(" and ", where))
            + " order by occurred_at desc, id desc limit :limit";
    params.addValue("limit", query.limit() + 1);
    List<AuditRecord> rows = jdbc.query(sql, params, (rs, i) -> map(rs));
    if (rows.size() <= query.limit()) {
      return new AuditPage(rows, null);
    }
    List<AuditRecord> items = rows.subList(0, query.limit());
    return new AuditPage(items, encode(items.get(items.size() - 1)));
  }

  private void filter(List<String> where, MapSqlParameterSource params, AuditQuery query) {
    if (query.actorId() != null) {
      where.add("actor_id = :actorId");
      params.addValue("actorId", query.actorId());
    }
    if (query.action() != null) {
      where.add("action = :action");
      params.addValue("action", query.action());
    }
    if (query.subjectType() != null) {
      where.add("subject_type = :subjectType");
      params.addValue("subjectType", query.subjectType());
    }
    if (query.subjectId() != null) {
      where.add("subject_id = :subjectId");
      params.addValue("subjectId", query.subjectId());
    }
    if (query.from() != null) {
      where.add("occurred_at >= :from");
      params.addValue("from", Timestamp.from(query.from()));
    }
    if (query.to() != null) {
      where.add("occurred_at <= :to");
      params.addValue("to", Timestamp.from(query.to()));
    }
    if (query.cursor() != null) {
      Cursor cursor = decode(query.cursor());
      where.add("(occurred_at, id) < (:cursorAt, :cursorId)");
      params.addValue("cursorAt", Timestamp.from(cursor.at()));
      params.addValue("cursorId", cursor.id());
    }
  }

  private AuditRecord map(ResultSet rs) throws SQLException {
    return new AuditRecord(
        rs.getObject("id", UUID.class),
        rs.getTimestamp("occurred_at").toInstant(),
        ActorType.valueOf(rs.getString("actor_type")),
        rs.getObject("actor_id", UUID.class),
        rs.getObject("actor_org_id", UUID.class),
        rs.getString("action"),
        rs.getString("subject_type"),
        rs.getString("subject_id"),
        AuditOutcome.valueOf(rs.getString("outcome")),
        rs.getString("ip"),
        rs.getString("user_agent"),
        rs.getString("correlation_id"),
        json.readValue(rs.getString("details"), DETAILS));
  }

  private record Cursor(Instant at, UUID id) {}

  private static String encode(AuditRecord last) {
    String raw = last.occurredAt().truncatedTo(ChronoUnit.MICROS) + "|" + last.id();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
  }

  private static Cursor decode(String cursor) {
    try {
      String[] parts = new String(Base64.getUrlDecoder().decode(cursor)).split("\\|");
      return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (RuntimeException e) {
      throw new InvalidAuditQueryException("malformed cursor");
    }
  }
}
