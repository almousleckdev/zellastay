package com.zellastay.identity.domain;

import com.zellastay.identity.api.EmailInvalidException;
import java.util.Locale;
import java.util.regex.Pattern;

/** A normalized email address (trimmed, lower-cased). */
public record Email(String value) {

  private static final int MAX_LENGTH = 254;
  private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

  public static Email of(String raw) {
    if (raw == null) {
      throw new EmailInvalidException();
    }
    String normalized = raw.strip().toLowerCase(Locale.ROOT);
    if (normalized.length() > MAX_LENGTH || !SHAPE.matcher(normalized).matches()) {
      throw new EmailInvalidException();
    }
    return new Email(normalized);
  }

  public String localPart() {
    return value.substring(0, value.indexOf('@'));
  }

  /** {@code jane.doe@example.com} becomes {@code j***@e***.com}; safe for logs and audit. */
  public String masked() {
    String domain = value.substring(value.indexOf('@') + 1);
    int dot = domain.lastIndexOf('.');
    return localPart().charAt(0) + "***@" + domain.charAt(0) + "***" + domain.substring(dot);
  }
}
