package com.zellastay.shared;

import java.time.Instant;
import java.util.UUID;

/** Common envelope of every domain event (EVENT_CATALOG.md). Payloads never carry secrets. */
public interface DomainEvent {

  UUID eventId();

  Instant occurredAt();

  int schemaVersion();

  String correlationId();
}
