package com.zellastay.identity.application;

import java.time.Instant;
import java.util.UUID;

/** One of the signed-in user's own sessions, flagged when it is the one making the request. */
public record SessionSummary(UUID id, Instant createdAt, Instant lastUsedAt, boolean current) {}
