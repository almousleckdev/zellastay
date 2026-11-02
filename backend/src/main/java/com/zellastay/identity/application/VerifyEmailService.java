package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.VerificationAttemptsExceededException;
import com.zellastay.identity.api.VerificationInvalidException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.ChallengeSettings;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.VerificationChallenge;
import com.zellastay.identity.domain.VerificationChallenge.Result;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies an email code. Every failure looks the same except an exhausted attempt budget. */
@Service
public class VerifyEmailService {

  private final AccountRepository accounts;
  private final ChallengeRepository challenges;
  private final SecretHasher hasher;
  private final ChallengeSettings settings;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  VerifyEmailService(
      AccountRepository accounts,
      ChallengeRepository challenges,
      SecretHasher hasher,
      ChallengeSettings settings,
      AuditRecorder audit,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.challenges = challenges;
    this.hasher = hasher;
    this.settings = settings;
    this.audit = audit;
    this.transaction = transaction;
    this.clock = clock;
  }

  public void verify(AccountKind kind, String rawEmail, String code) {
    Email email = Email.of(rawEmail);
    Result result = transaction.execute(status -> attempt(kind, email, code));
    switch (result) {
      case VERIFIED -> {}
      case BURNED -> throw new VerificationAttemptsExceededException();
      default -> throw new VerificationInvalidException();
    }
  }

  private Result attempt(AccountKind kind, Email email, String code) {
    Optional<Account> found = accounts.findByEmail(kind, email);
    if (found.isEmpty()) {
      hasher.keyed(code);
      return Result.WRONG;
    }
    Account account = found.get();
    Optional<VerificationChallenge> latest =
        challenges.findLatest(account.id(), ChallengePurpose.EMAIL_VERIFICATION);
    if (latest.isEmpty()) {
      return Result.WRONG;
    }
    VerificationChallenge challenge = latest.get();
    Instant now = clock.instant();
    boolean matches = hasher.sameSecret(challenge.secretHash(), hasher.keyed(code));
    Result result = challenge.check(matches, now, settings.maxAttempts());
    challenges.update(challenge);
    if (result == Result.VERIFIED) {
      account.verifyEmail(now);
      accounts.update(account);
      audit.record(
          new AuditEntry(
              AuditActors.actor(account),
              IdentityAuditAction.EMAIL_VERIFIED,
              AuditActors.subject(account),
              AuditOutcome.SUCCESS,
              Map.of()));
    }
    return result;
  }
}
