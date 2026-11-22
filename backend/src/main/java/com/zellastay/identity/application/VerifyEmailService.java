package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.VerificationAttemptsExceededException;
import com.zellastay.identity.api.VerificationInvalidException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.ChallengeCheckResult;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.ChallengeSettings;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.VerificationChallenge;
import java.time.Clock;
import java.time.Instant;
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
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;
  private final Clock clock;

  VerifyEmailService(
      AccountRepository accounts,
      ChallengeRepository challenges,
      SecretHasher hasher,
      ChallengeSettings settings,
      IdentityAuditTrail trail,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.challenges = challenges;
    this.hasher = hasher;
    this.settings = settings;
    this.trail = trail;
    this.transaction = transaction;
    this.clock = clock;
  }

  public void verify(AccountKind kind, String rawEmail, String code) {
    Email email = Email.of(rawEmail);
    ChallengeCheckResult result = transaction.execute(status -> attempt(kind, email, code));
    switch (result) {
      case VERIFIED -> {}
      case BURNED -> throw new VerificationAttemptsExceededException();
      default -> throw new VerificationInvalidException();
    }
  }

  private ChallengeCheckResult attempt(AccountKind kind, Email email, String code) {
    Optional<Account> found = accounts.findByEmail(kind, email);
    if (found.isEmpty()) {
      hasher.keyed(code);
      return ChallengeCheckResult.WRONG;
    }
    Account account = found.get();
    Optional<VerificationChallenge> latest =
        challenges.findLatest(account.id(), ChallengePurpose.EMAIL_VERIFICATION);
    if (latest.isEmpty()) {
      return ChallengeCheckResult.WRONG;
    }
    VerificationChallenge challenge = latest.get();
    Instant now = clock.instant();
    boolean matches = hasher.sameSecret(challenge.secretHash(), hasher.keyed(code));
    ChallengeCheckResult result = challenge.check(matches, now, settings.maxAttempts());
    challenges.update(challenge);
    if (result == ChallengeCheckResult.VERIFIED) {
      account.verifyEmail(now);
      accounts.update(account);
      trail.succeeded(account, IdentityAuditAction.EMAIL_VERIFIED);
    }
    return result;
  }
}
