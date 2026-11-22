package com.zellastay.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Counts recorded audit events, so tests assert on the trail without repeating SQL. */
@Component
public class AuditEvents {

  private final JdbcTemplate jdbc;

  AuditEvents(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int byActor(String action, Object actorId) {
    return count(
        "select count(*) from audit_event where action = ? and actor_id = ?", action, actorId);
  }

  public int byActorEmail(String action, String email) {
    return count(
        "select count(*) from audit_event where action = ?"
            + " and actor_id = (select id from ident_account where email = ?)",
        action,
        email);
  }

  public int bySubjectEmail(String action, String email) {
    return count(
        "select count(*) from audit_event where action = ?"
            + " and subject_id = (select id::text from ident_account where email = ?)",
        action,
        email);
  }

  private int count(String sql, Object... arguments) {
    Integer total = jdbc.queryForObject(sql, Integer.class, arguments);
    return total == null ? 0 : total;
  }
}
