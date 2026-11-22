package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.api.ResendTooSoonException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.Email;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resends a verification code. Silent when nothing is sent, so it reveals nothing about accounts.
 */
@Service
public class ResendVerificationService {

  private final AccountRepository accounts;
  private final ChallengeIssuer challenges;
  private final QuietDelivery delivery;
  private final IdentityEvents events;
  private final TransactionTemplate transaction;

  ResendVerificationService(
      AccountRepository accounts,
      ChallengeIssuer challenges,
      QuietDelivery delivery,
      IdentityEvents events,
      TransactionTemplate transaction) {
    this.accounts = accounts;
    this.challenges = challenges;
    this.delivery = delivery;
    this.events = events;
    this.transaction = transaction;
  }

  public void resend(AccountKind kind, String rawEmail) {
    Email email = Email.of(rawEmail);
    Optional<DeliveryRequest> message = transaction.execute(status -> issue(kind, email));
    if (message != null) {
      message.ifPresent(delivery::send);
    }
  }

  private Optional<DeliveryRequest> issue(AccountKind kind, Email email) {
    Optional<Account> found = accounts.findByEmail(kind, email);
    if (found.isEmpty() || found.get().status() != AccountStatus.PENDING_VERIFICATION) {
      return Optional.empty();
    }
    Account account = found.get();
    try {
      String code = challenges.issueCode(account);
      events.verificationRequested(account.id().value(), "EMAIL_VERIFICATION");
      return Optional.of(
          new DeliveryRequest(
              DeliveryTemplate.EMAIL_VERIFICATION,
              email.value(),
              account.snapshot().locale(),
              code));
    } catch (ResendTooSoonException tooSoon) {
      return Optional.empty();
    }
  }
}
