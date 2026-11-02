package com.zellastay.audit.api;

import com.zellastay.shared.ErrorCategory;
import com.zellastay.shared.ErrorCode;

public enum AuditErrorCode implements ErrorCode {
  ENTRY_INVALID("audit.entry-invalid", ErrorCategory.INTERNAL),
  UNAVAILABLE("audit.unavailable", ErrorCategory.UNAVAILABLE),
  QUERY_INVALID("audit.query-invalid", ErrorCategory.VALIDATION);

  private final String code;
  private final ErrorCategory category;

  AuditErrorCode(String code, ErrorCategory category) {
    this.code = code;
    this.category = category;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public ErrorCategory category() {
    return category;
  }
}
