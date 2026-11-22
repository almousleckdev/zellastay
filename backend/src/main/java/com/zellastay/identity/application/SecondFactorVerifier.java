package com.zellastay.identity.application;

import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.Base32;
import com.zellastay.identity.domain.MfaEnrollment;
import com.zellastay.identity.domain.MfaRepository;
import com.zellastay.identity.domain.SecretCipher;
import com.zellastay.identity.domain.Totp;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/** Checks an authenticator code or a recovery code against the admin's enrolment. */
@Component
class SecondFactorVerifier {

  private final MfaRepository mfa;
  private final SecretCipher cipher;
  private final RecoveryCodes recoveryCodes;

  SecondFactorVerifier(MfaRepository mfa, SecretCipher cipher, RecoveryCodes recoveryCodes) {
    this.mfa = mfa;
    this.cipher = cipher;
    this.recoveryCodes = recoveryCodes;
  }

  /** Empty when the admin has no enrolment at all. */
  Optional<FactorCheck> check(Account account, String code, String recoveryCode, Instant now) {
    return mfa.find(account.id())
        .map(enrollment -> check(account, enrollment, code, recoveryCode, now));
  }

  private FactorCheck check(
      Account account, MfaEnrollment enrollment, String code, String recoveryCode, Instant now) {
    boolean firstEnrolment = !enrollment.confirmed();
    if (recoveryCode != null && !recoveryCode.isBlank()) {
      boolean accepted =
          enrollment.confirmed() && recoveryCodes.consume(account.id(), recoveryCode, now);
      return new FactorCheck(accepted, true, OptionalLong.empty(), firstEnrolment);
    }
    byte[] secret = Base32.decode(cipher.decrypt(enrollment.secretCiphertext()));
    OptionalLong step = Totp.verify(secret, code, now, enrollment.lastUsedStep());
    return new FactorCheck(step.isPresent(), false, step, firstEnrolment);
  }
}
