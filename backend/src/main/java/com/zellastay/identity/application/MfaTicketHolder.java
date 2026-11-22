package com.zellastay.identity.application;

import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.VerificationChallenge;
import java.time.Instant;

/** A second-factor ticket resolved to its challenge and admin, with the secret already compared. */
record MfaTicketHolder(VerificationChallenge challenge, Account account, boolean secretMatches) {

  boolean isUsable(Instant now, int maxAttempts) {
    return secretMatches && challenge.isOpen(now, maxAttempts);
  }
}
