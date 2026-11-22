package com.zellastay.identity.application;

import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.SecretGenerator;
import org.springframework.stereotype.Component;

/** What a new administrator needs to set their own password: a placeholder and a setup link. */
@Component
class AdminInvitations {

  private final PasswordHasher hasher;
  private final SecretGenerator generator;
  private final ChallengeIssuer challenges;

  AdminInvitations(PasswordHasher hasher, SecretGenerator generator, ChallengeIssuer challenges) {
    this.hasher = hasher;
    this.generator = generator;
    this.challenges = challenges;
  }

  /** The hash of a random secret nobody knows, so the account cannot be signed into yet. */
  String unusablePasswordHash() {
    return hasher.hash(generator.token());
  }

  /** The invitation message carrying a single-use password-setup token. */
  DeliveryRequest invitationFor(Account account) {
    return new DeliveryRequest(
        DeliveryTemplate.ADMIN_INVITATION,
        account.snapshot().email().value(),
        account.snapshot().locale(),
        challenges.issueResetToken(account));
  }
}
