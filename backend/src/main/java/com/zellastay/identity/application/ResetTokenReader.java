package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.SecretHasher;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves the token of an emailed reset link. It answers only "is there a reset challenge for an
 * account of this kind that may reset its password"; why not is never revealed, and a malformed
 * token costs the same hashing time as a real one.
 */
@Component
class ResetTokenReader {

  private final ChallengeRepository challenges;
  private final AccountRepository accounts;
  private final SecretHasher secrets;

  ResetTokenReader(
      ChallengeRepository challenges, AccountRepository accounts, SecretHasher secrets) {
    this.challenges = challenges;
    this.accounts = accounts;
    this.secrets = secrets;
  }

  Optional<ResetTokenHolder> read(AccountKind kind, String rawToken) {
    Optional<ChallengeToken> token = ChallengeToken.parse(rawToken);
    if (token.isEmpty()) {
      secrets.digest(rawToken == null ? "" : rawToken);
      return Optional.empty();
    }
    return challenges
        .findById(token.get().id())
        .filter(challenge -> challenge.purpose() == ChallengePurpose.PASSWORD_RESET)
        .flatMap(
            challenge ->
                accounts
                    .findById(challenge.accountId())
                    .filter(account -> account.snapshot().kind() == kind)
                    .filter(account -> account.canResetPassword())
                    .map(
                        account ->
                            new ResetTokenHolder(
                                challenge,
                                account,
                                secrets.sameSecret(
                                    challenge.secretHash(),
                                    secrets.digest(token.get().secret())))));
  }
}
