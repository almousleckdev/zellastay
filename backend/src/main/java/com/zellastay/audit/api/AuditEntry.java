package com.zellastay.audit.api;

import java.util.Map;
import java.util.Objects;

/** One thing to record. Request metadata (request id, IP, user agent) is added by the recorder. */
public record AuditEntry(
    AuditActor actor,
    AuditAction action,
    AuditSubject subject,
    AuditOutcome outcome,
    Map<String, Object> details) {

  public AuditEntry {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(outcome, "outcome");
    details = details == null ? Map.of() : Map.copyOf(details);
  }
}
