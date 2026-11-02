package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.PasswordPolicy;
import com.zellastay.identity.domain.Profile;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers an account. The caller always gets the same outcome whether or not the email is already
 * registered; an existing owner is told by message instead.
 */
@Service
public class RegisterAccountService {

  private final AccountRepository accounts;
  private final PasswordPolicy policy;
  private final PasswordHasher hasher;
  private final ChallengeIssuer challenges;
  private final QuietDelivery delivery;
  private final IdentityEvents events;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  RegisterAccountService(
      AccountRepository accounts,
      PasswordPolicy policy,
      PasswordHasher hasher,
      ChallengeIssuer challenges,
      QuietDelivery delivery,
      IdentityEvents events,
      AuditRecorder audit,
      TransactionTemplate transaction,
      Clock clock) {
    this.accounts = accounts;
    this.policy = policy;
    this.hasher = hasher;
    this.challenges = challenges;
    this.delivery = delivery;
    this.events = events;
    this.audit = audit;
    this.transaction = transaction;
    this.clock = clock;
  }

  public void register(RegisterCommand command) {
    Email email = Email.of(command.email());
    policy.check(command.password(), email);
    String hash = hasher.hash(command.password());
    try {
      Optional<String> code = transaction.execute(status -> createIfNew(command, email, hash));
      if (code != null && code.isPresent()) {
        delivery.send(
            new DeliveryRequest(
                DeliveryTemplate.EMAIL_VERIFICATION, email.value(), command.locale(), code.get()));
        return;
      }
    } catch (DuplicateKeyException lostRace) {
      // another request registered the same email first; handled like an existing account
    }
    notifyExistingOwner(command, email);
  }

  private Optional<String> createIfNew(RegisterCommand command, Email email, String hash) {
    if (accounts.findByEmail(command.kind(), email).isPresent()) {
      return Optional.empty();
    }
    Account account =
        Account.register(
            AccountId.random(),
            command.kind(),
            email,
            hash,
            new Profile(command.firstName().strip(), command.lastName().strip()),
            command.locale(),
            clock.instant());
    accounts.insert(account);
    String code = challenges.issueCode(account);
    audit.record(
        new AuditEntry(
            AuditActors.actor(account),
            IdentityAuditAction.ACCOUNT_REGISTERED,
            AuditActors.subject(account),
            AuditOutcome.SUCCESS,
            Map.of("kind", command.kind().name())));
    events.registered(account.id().value(), command.kind(), command.locale());
    events.verificationRequested(account.id().value(), "EMAIL_VERIFICATION");
    return Optional.of(code);
  }

  private void notifyExistingOwner(RegisterCommand command, Email email) {
    delivery.send(
        new DeliveryRequest(
            DeliveryTemplate.ACCOUNT_ALREADY_EXISTS, email.value(), command.locale(), null));
    audit.recordBestEffort(
        new AuditEntry(
            com.zellastay.audit.api.AuditActor.anonymous(),
            IdentityAuditAction.REGISTRATION_DUPLICATE,
            new com.zellastay.audit.api.AuditSubject("account", null),
            AuditOutcome.DENIED,
            Map.of("kind", command.kind().name())));
  }
}
