package com.zellastay.identity.application;

import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.VerificationChallenge;

/** A password-reset token resolved to its challenge and account, with the secret compared. */
record ResetTokenHolder(VerificationChallenge challenge, Account account, boolean secretMatches) {}
