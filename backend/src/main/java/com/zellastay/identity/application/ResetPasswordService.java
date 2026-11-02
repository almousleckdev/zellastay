package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.api.ResetTokenInvalidException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.ChallengeSettings;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.PasswordPolicy;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.VerificationChallenge;
import com.zellastay.identity.domain.VerificationChallenge.Result;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Completes a password reset with the emailed token. Every failure looks the same. */
@Service
public class ResetPasswordService {

  private record Outcome(Result result, Account account) {}

  private final AccountRepository accounts;
  private final ChallengeRepository challenges;
  private final SecretHasher secrets;
  private final PasswordHasher hasher;
  private final PasswordPolicy policy;
  private final ChallengeSettings settings;
  private final SessionRevoker revoker;
  private final QuietDelivery delivery;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  ResetPasswordService(
      AccountRepository accounts,
      ChallengeRepository challenges,
      SecretHasher secrets,
      PasswordHasher hasher,
      PasswordPolicy policy,
      ChallengeSettings settings,
      SessionRevoker revoker,
      QuietDelivery delivery,
      AuditRecorder audit,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.challenges = challenges;
    this.secrets = secrets;
    this.hasher = hasher;
    this.policy = policy;
    this.settings = settings;
    this.revoker = revoker;
    this.delivery = delivery;
    this.audit = audit;
    this.transaction = transaction;
    this.clock = clock;
  }

  public void reset(AccountKind kind, String token, String newPassword) {
    String[] parts = token == null ? new String[0] : token.split("\\.", -1);
    Optional<UUID> id = parseId(parts);
    if (id.isEmpty() || parts.length != 2) {
      secrets.digest(token == null ? "" : token);
      throw new ResetTokenInvalidException();
    }
    String newHash = null;
    Optional<Account> owner = ownerOf(kind, id.get());
    if (owner.isPresent()) {
      policy.check(newPassword, owner.get().snapshot().email());
      newHash = hasher.hash(newPassword);
    }
    String hash = newHash;
    Outcome outcome = transaction.execute(status -> consume(kind, id.get(), parts[1], hash));
    if (outcome == null || outcome.result() != Result.VERIFIED) {
      throw new ResetTokenInvalidException();
    }
    Account account = outcome.account();
    delivery.send(
        new DeliveryRequest(
            DeliveryTemplate.PASSWORD_CHANGED,
            account.snapshot().email().value(),
            account.snapshot().locale(),
            null));
  }

  private Optional<Account> ownerOf(AccountKind kind, UUID challengeId) {
    return challenges
        .findById(challengeId)
        .filter(c -> c.purpose() == ChallengePurpose.PASSWORD_RESET)
        .flatMap(c -> accounts.findById(c.accountId()))
        .filter(account -> account.snapshot().kind() == kind)
        .filter(RequestPasswordResetService::canReset);
  }

  private Outcome consume(AccountKind kind, UUID challengeId, String secret, String newHash) {
    Optional<VerificationChallenge> found =
        challenges
            .findById(challengeId)
            .filter(c -> c.purpose() == ChallengePurpose.PASSWORD_RESET);
    Optional<Account> owner = found.flatMap(c -> accounts.findById(c.accountId()));
    if (found.isEmpty()
        || owner.isEmpty()
        || owner.get().snapshot().kind() != kind
        || !RequestPasswordResetService.canReset(owner.get())
        || newHash == null) {
      return new Outcome(Result.WRONG, null);
    }
    VerificationChallenge challenge = found.get();
    Account account = owner.get();
    Instant now = clock.instant();
    boolean matches = secrets.sameSecret(challenge.secretHash(), secrets.digest(secret));
    Result result = challenge.check(matches, now, settings.maxAttempts());
    challenges.update(challenge);
    if (result == Result.VERIFIED) {
      if (account.status() == AccountStatus.PENDING_VERIFICATION) {
        account.verifyEmail(now);
      }
      account.changePassword(newHash, now);
      accounts.update(account);
      revoker.revokeAll(account.id(), "password-reset");
      audit.record(
          new AuditEntry(
              AuditActors.actor(account),
              IdentityAuditAction.PASSWORD_RESET_COMPLETED,
              AuditActors.subject(account),
              AuditOutcome.SUCCESS,
              Map.of()));
    }
    return new Outcome(result, account);
  }

  private static Optional<UUID> parseId(String[] parts) {
    if (parts.length != 2) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(parts[0]));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
