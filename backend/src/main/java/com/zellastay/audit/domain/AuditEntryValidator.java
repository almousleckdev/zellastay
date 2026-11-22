package com.zellastay.audit.domain;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditEntryInvalidException;
import java.util.Map;
import java.util.regex.Pattern;

/** Keeps secrets and personal data out of the audit trail by checking every entry. */
public final class AuditEntryValidator {

  static final int MAX_VALUE_LENGTH = 200;
  static final int MAX_DETAILS = 20;

  private static final Pattern ACTION_CODE = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z0-9-]+)+$");
  private static final Pattern FORBIDDEN_KEY =
      Pattern.compile(
          "(?i)password|passwd|secret|token|otp|pepper|credential|authorization|cookie"
              + "|api_?key|private_?key|verification_?code|^code$");
  private static final Pattern EMAIL = Pattern.compile("[^@\\s*]+@[^@\\s]+\\.[^@\\s]+");
  private static final Pattern JWT_LIKE = Pattern.compile("^eyJ[\\w-]*\\.[\\w-]*\\.?[\\w-]*$");
  private static final int MAX_SUBJECT_TYPE = 64;
  private static final int MAX_SUBJECT_ID = 128;

  public void validate(AuditEntry entry) {
    String code = entry.action().code();
    if (code == null || !ACTION_CODE.matcher(code).matches()) {
      throw invalid("malformed action code " + code);
    }
    validateSubject(entry);
    Map<String, Object> details = entry.details();
    if (details.size() > MAX_DETAILS) {
      throw invalid("too many detail entries for " + code);
    }
    for (Map.Entry<String, Object> detail : details.entrySet()) {
      validateDetail(entry, detail.getKey(), detail.getValue());
    }
  }

  private void validateSubject(AuditEntry entry) {
    String type = entry.subject().type();
    String id = entry.subject().id();
    if (type.isBlank() || type.length() > MAX_SUBJECT_TYPE) {
      throw invalid("invalid subject type for " + entry.action().code());
    }
    if (id != null && (id.isBlank() || id.length() > MAX_SUBJECT_ID)) {
      throw invalid("invalid subject id for " + entry.action().code());
    }
  }

  private void validateDetail(AuditEntry entry, String key, Object value) {
    String code = entry.action().code();
    if (FORBIDDEN_KEY.matcher(key).find()) {
      throw invalid("secret-looking detail key '" + key + "' for " + code);
    }
    if (!entry.action().detailKeys().contains(key)) {
      throw invalid("detail key '" + key + "' is not declared by " + code);
    }
    if (value instanceof String text) {
      validateText(code, key, text);
    } else if (!(value instanceof Number) && !(value instanceof Boolean)) {
      throw invalid("detail '" + key + "' of " + code + " must be text, number or boolean");
    }
  }

  private void validateText(String code, String key, String text) {
    if (text.length() > MAX_VALUE_LENGTH) {
      throw invalid("detail '" + key + "' of " + code + " is too long");
    }
    if (EMAIL.matcher(text).find()) {
      throw invalid("detail '" + key + "' of " + code + " holds an unmasked email address");
    }
    if (JWT_LIKE.matcher(text).matches()) {
      throw invalid("detail '" + key + "' of " + code + " looks like a token");
    }
  }

  private static AuditEntryInvalidException invalid(String message) {
    return new AuditEntryInvalidException(message);
  }
}
