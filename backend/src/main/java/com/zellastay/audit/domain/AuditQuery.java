package com.zellastay.audit.domain;

import java.time.Instant;
import java.util.UUID;

/** Filters for the audit viewer; every filter is optional and filters combine with AND. */
public record AuditQuery(
    UUID actorId,
    String action,
    String subjectType,
    String subjectId,
    Instant from,
    Instant to,
    String cursor,
    int limit) {

  public static final int MAX_LIMIT = 100;

  public AuditQuery {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new InvalidAuditQueryException("limit must be between 1 and " + MAX_LIMIT);
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new InvalidAuditQueryException("from must not be after to");
    }
  }
}
