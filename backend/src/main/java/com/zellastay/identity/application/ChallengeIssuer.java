package com.zellastay.identity.application;

import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.ChallengeSettings;
import com.zellastay.identity.domain.ResendPolicy;
import com.zellastay.identity.domain.SecretGenerator;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.VerificationChallenge;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates a challenge and returns the plaintext secret, which exists only in the return value. */
@Component
class ChallengeIssuer {

  private final ChallengeRepository challenges;
  private final SecretHasher hasher;
  private final SecretGenerator generator;
  private final ChallengeSettings settings;
  private final Clock clock;

  ChallengeIssuer(
      ChallengeRepository challenges,
      SecretHasher hasher,
      SecretGenerator generator,
      ChallengeSettings settings,
      Clock clock) {
    this.challenges = challenges;
    this.hasher = hasher;
    this.generator = generator;
    this.settings = settings;
    this.clock = clock;
  }

  /** Six-digit code for email verification; enforces cooldown and hourly cap. */
  String issueCode(Account account) {
    Instant now = clock.instant();
    ResendPolicy.check(
        challenges.lastIssuedAt(account.id(), ChallengePurpose.EMAIL_VERIFICATION),
        challenges.countIssuedSince(
            account.id(), ChallengePurpose.EMAIL_VERIFICATION, now.minus(Duration.ofHours(1))),
        now,
        settings.resendCooldown(),
        settings.maxSendsPerHour());
    String code = generator.numericCode();
    insert(
        account, ChallengePurpose.EMAIL_VERIFICATION, hasher.keyed(code), settings.codeTtl(), now);
    return code;
  }

  /** Single-use link token {@code <challengeId>.<random>} for password reset. */
  String issueResetToken(Account account) {
    Instant now = clock.instant();
    ResendPolicy.check(
        challenges.lastIssuedAt(account.id(), ChallengePurpose.PASSWORD_RESET),
        challenges.countIssuedSince(
            account.id(), ChallengePurpose.PASSWORD_RESET, now.minus(Duration.ofHours(1))),
        now,
        settings.resendCooldown(),
        settings.maxSendsPerHour());
    String secret = generator.token();
    UUID id =
        insert(
            account,
            ChallengePurpose.PASSWORD_RESET,
            hasher.digest(secret),
            settings.resetTokenTtl(),
            now);
    return id + "." + secret;
  }

  private UUID insert(
      Account account, ChallengePurpose purpose, String hash, Duration ttl, Instant now) {
    UUID id = UUID.randomUUID();
    challenges.insert(VerificationChallenge.issue(id, account.id(), purpose, hash, now, ttl));
    return id;
  }
}
