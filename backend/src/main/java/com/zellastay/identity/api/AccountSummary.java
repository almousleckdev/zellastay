package com.zellastay.identity.api;

/** What other modules may know about an account: no credentials, no full email address. */
public record AccountSummary(
    AccountId id,
    AccountKind kind,
    AccountStatus status,
    boolean emailVerified,
    String maskedEmail,
    String locale) {}
