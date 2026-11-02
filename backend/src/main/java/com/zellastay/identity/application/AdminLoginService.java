package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.MfaRepository;
import com.zellastay.identity.domain.SecretGenerator;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.VerificationChallenge;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Step one of admin sign-in: the password. The result can only be used for the second factor. */
@Service
public class AdminLoginService {

  private final CredentialAuthenticator authenticator;
  private final ChallengeRepository challenges;
  private final MfaRepository mfa;
  private final SecretGenerator generator;
  private final SecretHasher hasher;
  private final Duration ticketTtl;
  private final TransactionTemplate transaction;
  private final Clock clock;

  AdminLoginService(
      CredentialAuthenticator authenticator,
      ChallengeRepository challenges,
      MfaRepository mfa,
      SecretGenerator generator,
      SecretHasher hasher,
      MfaSettings settings,
      TransactionTemplate transaction,
      Clock clock) {
    this.authenticator = authenticator;
    this.challenges = challenges;
    this.mfa = mfa;
    this.generator = generator;
    this.hasher = hasher;
    this.ticketTtl = settings.ticketTtl();
    this.transaction = transaction;
    this.clock = clock;
  }

  public MfaTicket login(String email, String password) {
    Account account = authenticator.authenticate(AccountKind.ADMIN, email, password);
    boolean enrolled = mfa.find(account.id()).map(e -> e.confirmed()).orElse(false);
    String secret = generator.token();
    UUID id = UUID.randomUUID();
    transaction.executeWithoutResult(
        status ->
            challenges.insert(
                VerificationChallenge.issue(
                    id,
                    account.id(),
                    ChallengePurpose.ADMIN_MFA_LOGIN,
                    hasher.digest(secret),
                    clock.instant(),
                    ticketTtl)));
    return new MfaTicket(id + "." + secret, enrolled);
  }
}
