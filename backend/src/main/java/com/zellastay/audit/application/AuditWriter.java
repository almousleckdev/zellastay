package com.zellastay.audit.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.domain.AuditRecord;
import com.zellastay.audit.domain.AuditStore;
import com.zellastay.audit.domain.RequestMetadata;
import com.zellastay.audit.domain.RequestMetadataProvider;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundaries for writing; the recorder chooses which one applies. */
@Service
class AuditWriter {

  private static final int MAX_USER_AGENT = 256;

  private final AuditStore store;
  private final RequestMetadataProvider requests;
  private final Clock clock;

  AuditWriter(AuditStore store, RequestMetadataProvider requests, Clock clock) {
    this.store = store;
    this.requests = requests;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  void appendInCallerTransaction(AuditEntry entry) {
    store.append(toRecord(entry));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void appendInNewTransaction(AuditEntry entry) {
    store.append(toRecord(entry));
  }

  private AuditRecord toRecord(AuditEntry entry) {
    RequestMetadata request = requests.current();
    return new AuditRecord(
        UUID.randomUUID(),
        clock.instant(),
        entry.actor().type(),
        entry.actor().id(),
        entry.actor().organizationId(),
        entry.action().code(),
        entry.subject().type(),
        entry.subject().id(),
        entry.outcome(),
        request.ip(),
        truncate(request.userAgent()),
        request.requestId(),
        entry.details());
  }

  private static String truncate(String userAgent) {
    if (userAgent == null || userAgent.length() <= MAX_USER_AGENT) {
      return userAgent;
    }
    return userAgent.substring(0, MAX_USER_AGENT);
  }
}
