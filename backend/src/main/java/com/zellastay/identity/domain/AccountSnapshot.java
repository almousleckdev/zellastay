package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import java.time.Instant;

/** Every persisted field of an account; the only way state crosses the repository boundary. */
public record AccountSnapshot(
    AccountId id,
    AccountKind kind,
    Email email,
    Instant emailVerifiedAt,
    String passwordHash,
    AccountStatus status,
    Profile profile,
    String locale,
    int failedLoginCount,
    Instant lockedUntil,
    Instant passwordChangedAt,
    Instant lastLoginAt,
    Instant createdAt,
    long version) {}
