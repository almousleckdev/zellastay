package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.domain.Account;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Writes identity's audit entries, so use cases state what happened, not how it is recorded.
 * Methods without "BestEffort" run inside the caller's transaction and fail closed; best-effort
 * methods never fail the caller.
 */
@Component
class IdentityAuditTrail {

  private final AuditRecorder audit;

  IdentityAuditTrail(AuditRecorder audit) {
    this.audit = audit;
  }

  void succeeded(Account account, IdentityAuditAction action) {
    succeeded(account, action, Map.of());
  }

  void succeeded(Account account, IdentityAuditAction action, Map<String, Object> details) {
    audit.record(entry(account, action, AuditOutcome.SUCCESS, details));
  }

  /** The platform itself acted on the account. */
  void systemAction(Account subject, IdentityAuditAction action) {
    audit.record(
        new AuditEntry(
            AuditActor.system(),
            action,
            AuditActors.subject(subject),
            AuditOutcome.SUCCESS,
            Map.of()));
  }

  /** An administrator acted on another account. */
  void adminAction(
      CurrentPrincipal actor,
      IdentityAuditAction action,
      AuditSubject subject,
      Map<String, Object> details) {
    audit.record(
        new AuditEntry(AuditActors.actor(actor), action, subject, AuditOutcome.SUCCESS, details));
  }

  void succeededBestEffort(
      Account account, IdentityAuditAction action, Map<String, Object> details) {
    audit.recordBestEffort(entry(account, action, AuditOutcome.SUCCESS, details));
  }

  /** A signed-in user acting on their own account. */
  void succeededBestEffort(
      CurrentPrincipal principal, IdentityAuditAction action, Map<String, Object> details) {
    audit.recordBestEffort(
        new AuditEntry(
            AuditActors.actor(principal),
            action,
            AuditActors.subject(principal.accountId()),
            AuditOutcome.SUCCESS,
            details));
  }

  void deniedBestEffort(Account account, IdentityAuditAction action, Map<String, Object> details) {
    audit.recordBestEffort(entry(account, action, AuditOutcome.DENIED, details));
  }

  void failedBestEffort(Account account, IdentityAuditAction action, Map<String, Object> details) {
    audit.recordBestEffort(entry(account, action, AuditOutcome.FAILURE, details));
  }

  /** Nobody could be identified: the actor is anonymous and the account unknown. */
  void anonymousBestEffort(
      IdentityAuditAction action, AuditOutcome outcome, Map<String, Object> details) {
    audit.recordBestEffort(
        new AuditEntry(
            AuditActor.anonymous(), action, AuditActors.unknownAccount(), outcome, details));
  }

  private static AuditEntry entry(
      Account account,
      IdentityAuditAction action,
      AuditOutcome outcome,
      Map<String, Object> details) {
    return new AuditEntry(
        AuditActors.actor(account), action, AuditActors.subject(account), outcome, details);
  }
}
