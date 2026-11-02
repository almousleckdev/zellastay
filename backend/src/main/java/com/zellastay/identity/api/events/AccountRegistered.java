package com.zellastay.identity.api.events;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record AccountRegistered(
    UUID eventId,
    Instant occurredAt,
    int schemaVersion,
    String correlationId,
    UUID accountId,
    AccountKind kind,
    String locale)
    implements DomainEvent {}
