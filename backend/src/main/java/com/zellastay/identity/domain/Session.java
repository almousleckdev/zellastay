package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import com.zellastay.shared.security.TokenAudience;
import java.time.Instant;
import java.util.UUID;

/** One login. Refresh tokens belong to it; revoking it invalidates all of them at once. */
public final class Session {

  private final UUID id;
  private final AccountId accountId;
  private final TokenAudience audience;
  private final boolean mfaVerified;
  private final Instant createdAt;
  private final Instant absoluteExpiresAt;
  private final java.time.Duration idleTimeout;
  private Instant lastUsedAt;
  private Instant revokedAt;
  private String revokedReason;

  private Session(
      UUID id,
      AccountId accountId,
      TokenAudience audience,
      boolean mfaVerified,
      Instant createdAt,
      Instant absoluteExpiresAt,
      java.time.Duration idleTimeout,
      Instant lastUsedAt,
      Instant revokedAt,
      String revokedReason) {
    this.id = id;
    this.accountId = accountId;
    this.audience = audience;
    this.mfaVerified = mfaVerified;
    this.createdAt = createdAt;
    this.absoluteExpiresAt = absoluteExpiresAt;
    this.idleTimeout = idleTimeout;
    this.lastUsedAt = lastUsedAt;
    this.revokedAt = revokedAt;
    this.revokedReason = revokedReason;
  }

  public static Session start(
      UUID id,
      AccountId accountId,
      TokenAudience audience,
      boolean mfaVerified,
      Instant now,
      SessionLifetime lifetime) {
    return new Session(
        id,
        accountId,
        audience,
        mfaVerified,
        now,
        now.plus(lifetime.absolute()),
        lifetime.idle(),
        now,
        null,
        null);
  }

  public static Session restore(
      UUID id,
      AccountId accountId,
      TokenAudience audience,
      boolean mfaVerified,
      Instant createdAt,
      Instant absoluteExpiresAt,
      java.time.Duration idleTimeout,
      Instant lastUsedAt,
      Instant revokedAt,
      String revokedReason) {
    return new Session(
        id,
        accountId,
        audience,
        mfaVerified,
        createdAt,
        absoluteExpiresAt,
        idleTimeout,
        lastUsedAt,
        revokedAt,
        revokedReason);
  }

  public boolean isActive(Instant now) {
    return revokedAt == null
        && now.isBefore(absoluteExpiresAt)
        && now.isBefore(lastUsedAt.plus(idleTimeout));
  }

  public void touch(Instant now) {
    lastUsedAt = now;
  }

  public void revoke(String reason, Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
      revokedReason = reason;
    }
  }

  public UUID id() {
    return id;
  }

  public AccountId accountId() {
    return accountId;
  }

  public TokenAudience audience() {
    return audience;
  }

  public boolean mfaVerified() {
    return mfaVerified;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant absoluteExpiresAt() {
    return absoluteExpiresAt;
  }

  public java.time.Duration idleTimeout() {
    return idleTimeout;
  }

  public Instant lastUsedAt() {
    return lastUsedAt;
  }

  public Instant revokedAt() {
    return revokedAt;
  }

  public String revokedReason() {
    return revokedReason;
  }
}
