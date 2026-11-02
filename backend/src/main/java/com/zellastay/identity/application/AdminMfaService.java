package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.api.IllegalAccountStateException;
import com.zellastay.identity.api.MfaInvalidException;
import com.zellastay.identity.api.MfaRequiredException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.Base32;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.MfaEnrollment;
import com.zellastay.identity.domain.MfaRepository;
import com.zellastay.identity.domain.SecretCipher;
import com.zellastay.identity.domain.SecretGenerator;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.Totp;
import com.zellastay.identity.domain.VerificationChallenge;
import com.zellastay.identity.domain.VerificationChallenge.Result;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Step two of admin sign-in: authenticator enrolment and the TOTP or recovery-code check. */
@Service
public class AdminMfaService {

  static final int RECOVERY_CODES = 10;

  private enum Failure {
    NONE,
    TICKET,
    CODE
  }

  private record Outcome(Failure failure, Account account, AdminSession session) {}

  private final AccountRepository accounts;
  private final ChallengeRepository challenges;
  private final MfaRepository mfa;
  private final SecretCipher cipher;
  private final SecretHasher hasher;
  private final SecretGenerator generator;
  private final SessionIssuer sessions;
  private final MfaSettings settings;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  AdminMfaService(
      AccountRepository accounts,
      ChallengeRepository challenges,
      MfaRepository mfa,
      SecretCipher cipher,
      SecretHasher hasher,
      SecretGenerator generator,
      SessionIssuer sessions,
      MfaSettings settings,
      AuditRecorder audit,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.challenges = challenges;
    this.mfa = mfa;
    this.cipher = cipher;
    this.hasher = hasher;
    this.generator = generator;
    this.sessions = sessions;
    this.settings = settings;
    this.audit = audit;
    this.transaction = transaction;
    this.clock = clock;
  }

  /** Creates (or replaces an unconfirmed) authenticator secret for the ticket's admin. */
  public MfaEnrollmentView enroll(String ticket) {
    return transaction.execute(
        status -> {
          Account account = openTicket(ticket).orElseThrow(MfaRequiredException::new);
          if (mfa.find(account.id()).map(MfaEnrollment::confirmed).orElse(false)) {
            throw new IllegalAccountStateException("a second factor is already enrolled");
          }
          byte[] secret = Totp.newSecret();
          mfa.savePending(account.id(), cipher.encrypt(Base32.encode(secret)));
          return new MfaEnrollmentView(
              Base32.encode(secret),
              Totp.provisioningUri(
                  settings.issuerLabel(), account.snapshot().email().value(), secret));
        });
  }

  /** Completes sign-in with an authenticator code or a one-time recovery code. */
  public AdminSession verify(String ticket, String code, String recoveryCode) {
    Outcome outcome = transaction.execute(status -> attempt(ticket, code, recoveryCode));
    if (outcome.failure() == Failure.NONE) {
      return outcome.session();
    }
    if (outcome.account() != null) {
      audit.recordBestEffort(
          new AuditEntry(
              AuditActors.actor(outcome.account()),
              IdentityAuditAction.ADMIN_MFA_FAILED,
              AuditActors.subject(outcome.account()),
              AuditOutcome.FAILURE,
              Map.of()));
    }
    throw outcome.failure() == Failure.CODE
        ? new MfaInvalidException()
        : new MfaRequiredException();
  }

  private Outcome attempt(String ticket, String code, String recoveryCode) {
    Parsed parsed = parse(ticket);
    if (parsed == null) {
      return failed(Failure.TICKET, null);
    }
    Optional<VerificationChallenge> found =
        challenges
            .findById(parsed.id())
            .filter(c -> c.purpose() == ChallengePurpose.ADMIN_MFA_LOGIN);
    Optional<Account> owner = found.flatMap(c -> accounts.findById(c.accountId()));
    if (owner.isEmpty()) {
      return failed(Failure.TICKET, null);
    }
    VerificationChallenge challenge = found.get();
    Account account = owner.get();
    Instant now = clock.instant();
    boolean ticketMatches =
        hasher.sameSecret(challenge.secretHash(), hasher.digest(parsed.secret()));
    if (!ticketMatches || !challenge.isOpen(now, settings.maxAttempts())) {
      return failed(Failure.TICKET, ticketMatches ? account : null);
    }
    Optional<MfaEnrollment> enrollment = mfa.find(account.id());
    if (enrollment.isEmpty()) {
      return failed(Failure.TICKET, account);
    }
    boolean recovery = recoveryCode != null && !recoveryCode.isBlank();
    OptionalLong step = OptionalLong.empty();
    boolean ok;
    if (recovery) {
      ok =
          enrollment.get().confirmed()
              && mfa.consumeRecoveryCode(account.id(), recoveryHash(recoveryCode), now);
    } else {
      byte[] secret = Base32.decode(cipher.decrypt(enrollment.get().secretCiphertext()));
      step = Totp.verify(secret, code, now, enrollment.get().lastUsedStep());
      ok = step.isPresent();
    }
    Result result = challenge.check(ok, now, settings.maxAttempts());
    challenges.update(challenge);
    if (result != Result.VERIFIED) {
      return failed(result == Result.WRONG ? Failure.CODE : Failure.TICKET, account);
    }
    return complete(account, enrollment.get(), recovery, step, now);
  }

  private Outcome complete(
      Account account, MfaEnrollment enrollment, boolean recovery, OptionalLong step, Instant now) {
    if (step.isPresent() && !enrollment.confirmed()) {
      mfa.confirm(account.id(), step.getAsLong(), now);
    } else if (step.isPresent() && !mfa.advanceStep(account.id(), step.getAsLong())) {
      return failed(Failure.CODE, account);
    }
    List<String> recoveryCodes = List.of();
    if (!enrollment.confirmed()) {
      recoveryCodes = newRecoveryCodes(account);
      record(account, IdentityAuditAction.ADMIN_MFA_ENROLLED);
    }
    if (recovery) {
      record(account, IdentityAuditAction.ADMIN_RECOVERY_CODE_USED);
    }
    record(account, IdentityAuditAction.ADMIN_MFA_VERIFIED);
    TokenPair tokens = sessions.start(account, true);
    return new Outcome(Failure.NONE, account, new AdminSession(tokens, recoveryCodes));
  }

  private List<String> newRecoveryCodes(Account account) {
    List<String> codes = new ArrayList<>();
    List<String> hashes = new ArrayList<>();
    for (int i = 0; i < RECOVERY_CODES; i++) {
      byte[] entropy = java.util.Base64.getUrlDecoder().decode(generator.token());
      String raw = Base32.encode(entropy).substring(0, 12);
      String code = raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);
      codes.add(code);
      hashes.add(recoveryHash(code));
    }
    mfa.replaceRecoveryCodes(account.id(), hashes);
    return codes;
  }

  private String recoveryHash(String code) {
    return hasher.keyed(code.replace("-", "").strip().toUpperCase(Locale.ROOT));
  }

  private void record(Account account, IdentityAuditAction action) {
    audit.record(
        new AuditEntry(
            AuditActors.actor(account),
            action,
            AuditActors.subject(account),
            AuditOutcome.SUCCESS,
            Map.of()));
  }

  private Optional<Account> openTicket(String ticket) {
    Parsed parsed = parse(ticket);
    if (parsed == null) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    return challenges
        .findById(parsed.id())
        .filter(c -> c.purpose() == ChallengePurpose.ADMIN_MFA_LOGIN)
        .filter(c -> hasher.sameSecret(c.secretHash(), hasher.digest(parsed.secret())))
        .filter(c -> c.isOpen(now, settings.maxAttempts()))
        .flatMap(c -> accounts.findById(c.accountId()));
  }

  private record Parsed(UUID id, String secret) {}

  private static Parsed parse(String ticket) {
    if (ticket == null) {
      return null;
    }
    String[] parts = ticket.split("\\.", -1);
    if (parts.length != 2) {
      return null;
    }
    try {
      return new Parsed(UUID.fromString(parts[0]), parts[1]);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Outcome failed(Failure failure, Account account) {
    return new Outcome(failure, account, null);
  }
}
