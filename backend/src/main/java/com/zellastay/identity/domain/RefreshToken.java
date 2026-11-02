package com.zellastay.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** A stored (hashed) refresh token; {@code replacedBy} links the rotation chain. */
public record RefreshToken(
    UUID id, UUID sessionId, String tokenHash, Instant issuedAt, Instant usedAt, UUID replacedBy) {}
