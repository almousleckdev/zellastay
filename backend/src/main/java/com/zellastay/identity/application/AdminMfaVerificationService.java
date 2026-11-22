package com.zellastay.identity.application;

import com.zellastay.identity.api.MfaInvalidException;
import com.zellastay.identity.api.MfaRequiredException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.ChallengeCheckResult;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.VerificationChallenge;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step two of admin sign-in: completes it with an authenticator or recovery code. Failed attempts
 * are counted on the ticket and must commit, so the outcome is returned and only then thrown.
 */
@Service
public class AdminMfaVerificationService {

  private enum Failure {
    NONE,
    TICKET,
    CODE
  }

  private record Outcome(Failure failure, Account account, AdminSession session) {}

  private final MfaTicketReader tickets;
  private final SecondFactorVerifier factors;
  private final AdminSignInCompletion completion;
  private final ChallengeRepository challenges;
  private final MfaSettings settings;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;
  private final Clock clock;

  AdminMfaVerificationService(
      MfaTicketReader tickets,
      SecondFactorVerifier factors,
      AdminSignInCompletion completion,
      ChallengeRepository challenges,
      MfaSettings settings,
      IdentityAuditTrail trail,
      TransactionTemplate transaction,
      Clock clock) {
    this.tickets = tickets;
    this.factors = factors;
    this.completion = completion;
    this.challenges = challenges;
    this.settings = settings;
    this.trail = trail;
    this.transaction = transaction;
    this.clock = clock;
  }

  /** Completes sign-in with an authenticator code or a one-time recovery code. */
  public AdminSession verify(String ticket, String code, String recoveryCode) {
    Outcome outcome = transaction.execute(status -> attempt(ticket, code, recoveryCode));
    if (outcome.failure() == Failure.NONE) {
      return outcome.session();
    }
    if (outcome.account() != null) {
      trail.failedBestEffort(outcome.account(), IdentityAuditAction.ADMIN_MFA_FAILED, Map.of());
    }
    throw outcome.failure() == Failure.CODE
        ? new MfaInvalidException()
        : new MfaRequiredException();
  }

  private Outcome attempt(String ticket, String code, String recoveryCode) {
    Optional<MfaTicketHolder> found = tickets.read(ticket);
    if (found.isEmpty()) {
      return failed(Failure.TICKET, null);
    }
    MfaTicketHolder holder = found.get();
    Account account = holder.account();
    Instant now = clock.instant();
    if (!holder.isUsable(now, settings.maxAttempts())) {
      return failed(Failure.TICKET, holder.secretMatches() ? account : null);
    }
    Optional<FactorCheck> factor = factors.check(account, code, recoveryCode, now);
    if (factor.isEmpty()) {
      return failed(Failure.TICKET, account);
    }
    ChallengeCheckResult result = countAttempt(holder.challenge(), factor.get(), now);
    if (result != ChallengeCheckResult.VERIFIED) {
      return failed(result == ChallengeCheckResult.WRONG ? Failure.CODE : Failure.TICKET, account);
    }
    return completion
        .complete(account, factor.get(), now)
        .map(session -> new Outcome(Failure.NONE, account, session))
        .orElseGet(() -> failed(Failure.CODE, account));
  }

  private ChallengeCheckResult countAttempt(
      VerificationChallenge challenge, FactorCheck factor, Instant now) {
    ChallengeCheckResult result = challenge.check(factor.accepted(), now, settings.maxAttempts());
    challenges.update(challenge);
    return result;
  }

  private static Outcome failed(Failure failure, Account account) {
    return new Outcome(failure, account, null);
  }
}
