package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import com.zellastay.shared.security.TokenAudience;
import java.time.Instant;
import java.util.UUID;

/** What a verified access token asserts. */
public record TokenClaims(
    AccountId accountId,
    TokenAudience audience,
    UUID sessionId,
    boolean mfaVerified,
    Instant expiresAt) {}
