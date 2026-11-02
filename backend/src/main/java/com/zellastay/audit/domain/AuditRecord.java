package com.zellastay.audit.domain;

import com.zellastay.audit.api.ActorType;
import com.zellastay.audit.api.AuditOutcome;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A stored audit event, as written and as read back by the viewer. */
public record AuditRecord(
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
    Map<String, Object> details) {}
