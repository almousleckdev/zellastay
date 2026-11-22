package com.zellastay.identity.application;

import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.MfaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Finishes an accepted second factor: records the enrolment state, audits, starts the session. */
@Component
class AdminSignInCompletion {

  private final MfaRepository mfa;
  private final RecoveryCodes recoveryCodes;
  private final SessionIssuer sessions;
  private final IdentityAuditTrail trail;

  AdminSignInCompletion(
      MfaRepository mfa,
      RecoveryCodes recoveryCodes,
      SessionIssuer sessions,
      IdentityAuditTrail trail) {
    this.mfa = mfa;
    this.recoveryCodes = recoveryCodes;
    this.sessions = sessions;
    this.trail = trail;
  }

  /** Empty when an authenticator code was already used for this period (a replay). */
  Optional<AdminSession> complete(Account account, FactorCheck factor, Instant now) {
    if (factor.totpStep().isPresent() && !recordStep(account, factor, now)) {
      return Optional.empty();
    }
    List<String> newRecoveryCodes = List.of();
    if (factor.firstEnrolment()) {
      newRecoveryCodes = recoveryCodes.replaceFor(account.id());
      trail.succeeded(account, IdentityAuditAction.ADMIN_MFA_ENROLLED);
    }
    if (factor.viaRecoveryCode()) {
      trail.succeeded(account, IdentityAuditAction.ADMIN_RECOVERY_CODE_USED);
    }
    trail.succeeded(account, IdentityAuditAction.ADMIN_MFA_VERIFIED);
    return Optional.of(new AdminSession(sessions.start(account, true), newRecoveryCodes));
  }

  private boolean recordStep(Account account, FactorCheck factor, Instant now) {
    long step = factor.totpStep().getAsLong();
    if (factor.firstEnrolment()) {
      mfa.confirm(account.id(), step, now);
      return true;
    }
    return mfa.advanceStep(account.id(), step);
  }
}
