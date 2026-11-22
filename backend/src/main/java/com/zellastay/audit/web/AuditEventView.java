package com.zellastay.audit.web;

import com.zellastay.audit.api.ActorType;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.domain.AuditRecord;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record AuditEventView(
    UUID id,
    Instant occurredAt,
    ActorType actorType,
    UUID actorId,
    UUID actorOrganizationId,
    String action,
    String subjectType,
    String subjectId,
    AuditOutcome outcome,
    String ip,
    String userAgent,
    String correlationId,
    Map<String, Object> details) {

  static AuditEventView from(AuditRecord record) {
    return new AuditEventView(
        record.id(),
        record.occurredAt(),
        record.actorType(),
        record.actorId(),
        record.actorOrganizationId(),
        record.action(),
        record.subjectType(),
        record.subjectId(),
        record.outcome(),
        record.ip(),
        record.userAgent(),
        record.correlationId(),
        record.details());
  }
}
