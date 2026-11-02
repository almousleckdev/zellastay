package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A one-time secret (code or link token) with expiry and an attempt budget. Stored hashed. */
public final class VerificationChallenge {

  public enum Result {
    VERIFIED,
    WRONG,
    BURNED,
    EXPIRED,
    USED
  }

  private final UUID id;
  private final AccountId accountId;
  private final ChallengePurpose purpose;
  private final String secretHash;
  private final Instant expiresAt;
  private final Instant createdAt;
  private int attempts;
  private Instant consumedAt;

  private VerificationChallenge(
      UUID id,
      AccountId accountId,
      ChallengePurpose purpose,
      String secretHash,
      Instant expiresAt,
      Instant createdAt,
      int attempts,
      Instant consumedAt) {
    this.id = id;
    this.accountId = accountId;
    this.purpose = purpose;
    this.secretHash = secretHash;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
    this.attempts = attempts;
    this.consumedAt = consumedAt;
  }

  public static VerificationChallenge issue(
      UUID id,
      AccountId accountId,
      ChallengePurpose purpose,
      String secretHash,
      Instant now,
      Duration ttl) {
    return new VerificationChallenge(
        id, accountId, purpose, secretHash, now.plus(ttl), now, 0, null);
  }

  public static VerificationChallenge restore(
      UUID id,
      AccountId accountId,
      ChallengePurpose purpose,
      String secretHash,
      Instant expiresAt,
      Instant createdAt,
      int attempts,
      Instant consumedAt) {
    return new VerificationChallenge(
        id, accountId, purpose, secretHash, expiresAt, createdAt, attempts, consumedAt);
  }

  /** True while the challenge could still be verified (no attempt is recorded). */
  public boolean isOpen(Instant now, int maxAttempts) {
    return consumedAt == null && now.isBefore(expiresAt) && attempts < maxAttempts;
  }

  /**
   * Records one attempt; {@code matches} says whether the presented secret equals the stored one.
   */
  public Result check(boolean matches, Instant now, int maxAttempts) {
    if (consumedAt != null) {
      return Result.USED;
    }
    if (!now.isBefore(expiresAt)) {
      return Result.EXPIRED;
    }
    if (attempts >= maxAttempts) {
      return Result.BURNED;
    }
    if (matches) {
      consumedAt = now;
      return Result.VERIFIED;
    }
    attempts++;
    return attempts >= maxAttempts ? Result.BURNED : Result.WRONG;
  }

  public UUID id() {
    return id;
  }

  public AccountId accountId() {
    return accountId;
  }

  public ChallengePurpose purpose() {
    return purpose;
  }

  public String secretHash() {
    return secretHash;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public int attempts() {
    return attempts;
  }

  public Instant consumedAt() {
    return consumedAt;
  }
}
