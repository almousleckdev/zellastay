package com.zellastay.identity.domain;

import com.zellastay.identity.api.PasswordRejectedException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Length 10 to 128 (after NFKC), no composition rules, no known-weak or account-derived values. */
public final class PasswordPolicy {

  static final int MIN_LENGTH = 10;
  static final int MAX_LENGTH = 128;

  private final Set<String> denylist;

  public PasswordPolicy(Set<String> denylist) {
    this.denylist = Set.copyOf(denylist);
  }

  public static String normalize(String password) {
    return Normalizer.normalize(password, Normalizer.Form.NFKC);
  }

  public void check(String password, Email email) {
    if (password == null) {
      throw new PasswordRejectedException("password is required");
    }
    String normalized = normalize(password);
    int length = normalized.codePointCount(0, normalized.length());
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      throw new PasswordRejectedException("password length is outside the allowed range");
    }
    String lowered = normalized.toLowerCase(Locale.ROOT);
    if (denylist.contains(lowered)) {
      throw new PasswordRejectedException("password is on the common-password list");
    }
    if (lowered.equals(email.value()) || lowered.equals(email.localPart())) {
      throw new PasswordRejectedException("password must not equal the email address");
    }
  }
}
