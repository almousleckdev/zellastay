package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.api.ResetTokenInvalidException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.ChallengeCheckResult;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.ChallengeSettings;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Completes a password reset with the emailed token. Every failure looks the same. */
@Service
public class ResetPasswordService {

  private record Outcome(ChallengeCheckResult result, Account account) {}

  private final ResetTokenReader tokens;
  private final ChallengeRepository challenges;
  private final PasswordHasher hasher;
  private final PasswordPolicy policy;
  private final ChallengeSettings settings;
  private final PasswordResetCompletion completion;
  private final QuietDelivery delivery;
  private final TransactionTemplate transaction;
  private final Clock clock;

  ResetPasswordService(
      ResetTokenReader tokens,
      ChallengeRepository challenges,
      PasswordHasher hasher,
      PasswordPolicy policy,
      ChallengeSettings settings,
      PasswordResetCompletion completion,
      QuietDelivery delivery,
      TransactionTemplate transaction,
      Clock clock) {
    this.tokens = tokens;
    this.challenges = challenges;
    this.hasher = hasher;
    this.policy = policy;
    this.settings = settings;
    this.completion = completion;
    this.delivery = delivery;
    this.transaction = transaction;
    this.clock = clock;
  }

  public void reset(AccountKind kind, String rawToken, String newPassword) {
    ResetTokenHolder holder =
        tokens.read(kind, rawToken).orElseThrow(ResetTokenInvalidException::new);
    policy.check(newPassword, holder.account().snapshot().email());
    String newHash = hasher.hash(newPassword);
    Outcome outcome = transaction.execute(status -> consume(kind, rawToken, newHash));
    if (outcome == null || outcome.result() != ChallengeCheckResult.VERIFIED) {
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

  private Outcome consume(AccountKind kind, String rawToken, String newHash) {
    Optional<ResetTokenHolder> found = tokens.read(kind, rawToken);
    if (found.isEmpty()) {
      return new Outcome(ChallengeCheckResult.WRONG, null);
    }
    ResetTokenHolder holder = found.get();
    Instant now = clock.instant();
    ChallengeCheckResult result =
        holder.challenge().check(holder.secretMatches(), now, settings.maxAttempts());
    challenges.update(holder.challenge());
    if (result == ChallengeCheckResult.VERIFIED) {
      completion.complete(holder.account(), newHash, now);
    }
    return new Outcome(result, holder.account());
  }
}
