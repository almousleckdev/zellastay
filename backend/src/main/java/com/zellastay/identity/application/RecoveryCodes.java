package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.domain.Base32;
import com.zellastay.identity.domain.MfaRepository;
import com.zellastay.identity.domain.SecretGenerator;
import com.zellastay.identity.domain.SecretHasher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** One-time codes that replace the authenticator; stored only as keyed hashes. */
@Component
class RecoveryCodes {

  static final int COUNT = 10;

  private final MfaRepository mfa;
  private final SecretGenerator generator;
  private final SecretHasher hasher;

  RecoveryCodes(MfaRepository mfa, SecretGenerator generator, SecretHasher hasher) {
    this.mfa = mfa;
    this.generator = generator;
    this.hasher = hasher;
  }

  /** Replaces all codes of the account and returns the new ones, which are never shown again. */
  List<String> replaceFor(AccountId accountId) {
    List<String> codes = new ArrayList<>();
    List<String> hashes = new ArrayList<>();
    for (int i = 0; i < COUNT; i++) {
      String code = newCode();
      codes.add(code);
      hashes.add(hash(code));
    }
    mfa.replaceRecoveryCodes(accountId, hashes);
    return codes;
  }

  /** True when the code was valid and has now been used up. */
  boolean consume(AccountId accountId, String code, Instant now) {
    return mfa.consumeRecoveryCode(accountId, hash(code), now);
  }

  private String newCode() {
    byte[] entropy = Base64.getUrlDecoder().decode(generator.token());
    String raw = Base32.encode(entropy).substring(0, 12);
    return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);
  }

  private String hash(String code) {
    return hasher.keyed(code.replace("-", "").strip().toUpperCase(Locale.ROOT));
  }
}
