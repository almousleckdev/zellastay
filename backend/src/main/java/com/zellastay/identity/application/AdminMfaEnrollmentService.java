package com.zellastay.identity.application;

import com.zellastay.identity.api.IllegalAccountStateException;
import com.zellastay.identity.api.MfaRequiredException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.Base32;
import com.zellastay.identity.domain.MfaEnrollment;
import com.zellastay.identity.domain.MfaRepository;
import com.zellastay.identity.domain.SecretCipher;
import com.zellastay.identity.domain.Totp;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step two of admin sign-in, first time: creates the authenticator secret for the ticket holder.
 */
@Service
public class AdminMfaEnrollmentService {

  private final MfaTicketReader tickets;
  private final MfaRepository mfa;
  private final SecretCipher cipher;
  private final MfaSettings settings;
  private final TransactionTemplate transaction;
  private final Clock clock;

  AdminMfaEnrollmentService(
      MfaTicketReader tickets,
      MfaRepository mfa,
      SecretCipher cipher,
      MfaSettings settings,
      TransactionTemplate transaction,
      Clock clock) {
    this.tickets = tickets;
    this.mfa = mfa;
    this.cipher = cipher;
    this.settings = settings;
    this.transaction = transaction;
    this.clock = clock;
  }

  /** Creates, or replaces an unconfirmed, authenticator secret for the ticket's admin. */
  public MfaEnrollmentView enroll(String ticket) {
    return transaction.execute(status -> enrollAdminOf(ticket));
  }

  private MfaEnrollmentView enrollAdminOf(String ticket) {
    Account account =
        tickets
            .read(ticket)
            .filter(holder -> holder.isUsable(clock.instant(), settings.maxAttempts()))
            .map(MfaTicketHolder::account)
            .orElseThrow(MfaRequiredException::new);
    if (mfa.find(account.id()).map(MfaEnrollment::confirmed).orElse(false)) {
      throw new IllegalAccountStateException("a second factor is already enrolled");
    }
    byte[] secret = Totp.newSecret();
    mfa.savePending(account.id(), cipher.encrypt(Base32.encode(secret)));
    return new MfaEnrollmentView(
        Base32.encode(secret),
        Totp.provisioningUri(settings.issuerLabel(), account.snapshot().email().value(), secret));
  }
}
