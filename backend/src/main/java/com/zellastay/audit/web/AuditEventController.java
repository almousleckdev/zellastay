package com.zellastay.audit.web;

import com.zellastay.audit.application.AuditQueryService;
import com.zellastay.audit.domain.AuditQuery;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin audit viewer. Requires the {@code audit.read} permission granted through admin roles. */
@RestController
class AuditEventController {

  private static final int DEFAULT_LIMIT = 50;

  private final AuditQueryService queries;

  AuditEventController(AuditQueryService queries) {
    this.queries = queries;
  }

  @GetMapping("/api/v1/admin/audit-events")
  @PreAuthorize("hasAuthority('PERM_audit.read')")
  AuditEventPageView find(
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String subjectType,
      @RequestParam(required = false) String subjectId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
    return AuditEventPageView.from(
        queries.find(
            new AuditQuery(actorId, action, subjectType, subjectId, from, to, cursor, limit)));
  }
}
