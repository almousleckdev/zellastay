package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.MfaEnrollment;
import com.zellastay.identity.domain.MfaRepository;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Step one of admin sign-in: the password. The result can only be used for the second factor. */
@Service
public class AdminLoginService {

  private final CredentialAuthenticator authenticator;
  private final MfaRepository mfa;
  private final ChallengeIssuer challenges;
  private final Duration ticketTtl;
  private final TransactionTemplate transaction;

  AdminLoginService(
      CredentialAuthenticator authenticator,
      MfaRepository mfa,
      ChallengeIssuer challenges,
      MfaSettings settings,
      TransactionTemplate transaction) {
    this.authenticator = authenticator;
    this.mfa = mfa;
    this.challenges = challenges;
    this.ticketTtl = settings.ticketTtl();
    this.transaction = transaction;
  }

  public MfaTicket login(String email, String password) {
    Account account = authenticator.authenticate(AccountKind.ADMIN, email, password);
    boolean enrolled = mfa.find(account.id()).map(MfaEnrollment::confirmed).orElse(false);
    String ticket = transaction.execute(status -> challenges.issueMfaTicket(account, ticketTtl));
    return new MfaTicket(ticket, enrolled);
  }
}
