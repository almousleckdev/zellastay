package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.api.EmailInvalidException;
import com.zellastay.identity.api.ResendTooSoonException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.Email;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Starts a password reset. Always silent: it never reveals whether an account exists. */
@Service
public class RequestPasswordResetService {

  private final AccountRepository accounts;
  private final ChallengeIssuer challenges;
  private final QuietDelivery delivery;
  private final IdentityEvents events;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;

  RequestPasswordResetService(
      AccountRepository accounts,
      ChallengeIssuer challenges,
      QuietDelivery delivery,
      IdentityEvents events,
      AuditRecorder audit,
      TransactionTemplate transaction) {
    this.accounts = accounts;
    this.challenges = challenges;
    this.delivery = delivery;
    this.events = events;
    this.audit = audit;
    this.transaction = transaction;
  }

  public void request(AccountKind kind, String rawEmail) {
    Optional<Email> email = parse(rawEmail);
    if (email.isEmpty()) {
      return;
    }
    Optional<DeliveryRequest> message = transaction.execute(status -> issue(kind, email.get()));
    if (message != null) {
      message.ifPresent(delivery::send);
    }
  }

  private Optional<DeliveryRequest> issue(AccountKind kind, Email email) {
    Optional<Account> found = accounts.findByEmail(kind, email);
    if (found.isEmpty() || !canReset(found.get())) {
      return Optional.empty();
    }
    Account account = found.get();
    try {
      String token = challenges.issueResetToken(account);
      events.passwordResetRequested(account.id().value());
      audit.record(
          new AuditEntry(
              AuditActors.actor(account),
              IdentityAuditAction.PASSWORD_RESET_REQUESTED,
              AuditActors.subject(account),
              AuditOutcome.SUCCESS,
              Map.of()));
      return Optional.of(
          new DeliveryRequest(
              DeliveryTemplate.PASSWORD_RESET, email.value(), account.snapshot().locale(), token));
    } catch (ResendTooSoonException tooSoon) {
      return Optional.empty();
    }
  }

  static boolean canReset(Account account) {
    return account.status() == AccountStatus.ACTIVE
        || account.status() == AccountStatus.PENDING_VERIFICATION;
  }

  private static Optional<Email> parse(String raw) {
    try {
      return Optional.of(Email.of(raw));
    } catch (EmailInvalidException invalid) {
      return Optional.empty();
    }
  }
}
