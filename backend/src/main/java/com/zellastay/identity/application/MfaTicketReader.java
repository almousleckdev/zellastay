package com.zellastay.identity.application;

import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.SecretHasher;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves the ticket string of admin sign-in step one; empty when it points at nothing real. */
@Component
class MfaTicketReader {

  private final ChallengeRepository challenges;
  private final AccountRepository accounts;
  private final SecretHasher hasher;

  MfaTicketReader(ChallengeRepository challenges, AccountRepository accounts, SecretHasher hasher) {
    this.challenges = challenges;
    this.accounts = accounts;
    this.hasher = hasher;
  }

  Optional<MfaTicketHolder> read(String ticket) {
    return ChallengeToken.parse(ticket).flatMap(this::resolve);
  }

  private Optional<MfaTicketHolder> resolve(ChallengeToken token) {
    return challenges
        .findById(token.id())
        .filter(challenge -> challenge.purpose() == ChallengePurpose.ADMIN_MFA_LOGIN)
        .flatMap(
            challenge ->
                accounts
                    .findById(challenge.accountId())
                    .map(
                        account ->
                            new MfaTicketHolder(
                                challenge,
                                account,
                                hasher.sameSecret(
                                    challenge.secretHash(), hasher.digest(token.secret())))));
  }
}
