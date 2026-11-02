package com.zellastay.identity.api.events;

import com.zellastay.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record PasswordResetRequested(
    UUID eventId, Instant occurredAt, int schemaVersion, String correlationId, UUID accountId)
    implements DomainEvent {}
