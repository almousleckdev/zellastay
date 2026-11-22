package com.zellastay.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads stored account rows, so tests can check persisted state after an API call. */
@Component
public class AccountRecords {

  private final JdbcTemplate jdbc;

  AccountRecords(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String statusOf(String email, String kind) {
    return jdbc.queryForObject(
        "select status from ident_account where email = ? and kind = ?", String.class, email, kind);
  }
}
