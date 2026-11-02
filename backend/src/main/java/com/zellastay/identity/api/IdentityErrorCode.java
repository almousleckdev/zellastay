package com.zellastay.identity.api;

import com.zellastay.shared.ErrorCategory;
import com.zellastay.shared.ErrorCode;

public enum IdentityErrorCode implements ErrorCode {
  INVALID_CREDENTIALS("identity.invalid-credentials", ErrorCategory.UNAUTHENTICATED),
  ACCOUNT_SUSPENDED("identity.account-suspended", ErrorCategory.FORBIDDEN),
  VERIFICATION_INVALID("identity.verification-invalid", ErrorCategory.VALIDATION),
  VERIFICATION_ATTEMPTS_EXCEEDED(
      "identity.verification-attempts-exceeded", ErrorCategory.RATE_LIMITED),
  RESEND_TOO_SOON("identity.resend-too-soon", ErrorCategory.RATE_LIMITED),
  RESET_TOKEN_INVALID("identity.reset-token-invalid", ErrorCategory.VALIDATION),
  PASSWORD_REJECTED("identity.password-rejected", ErrorCategory.VALIDATION),
  REFRESH_INVALID("identity.refresh-invalid", ErrorCategory.UNAUTHENTICATED),
  MFA_REQUIRED("identity.mfa-required", ErrorCategory.UNAUTHENTICATED),
  MFA_INVALID("identity.mfa-invalid", ErrorCategory.UNAUTHENTICATED),
  SESSION_NOT_FOUND("identity.session-not-found", ErrorCategory.NOT_FOUND),
  ACCOUNT_NOT_FOUND("identity.account-not-found", ErrorCategory.NOT_FOUND),
  ILLEGAL_ACCOUNT_STATE("identity.illegal-account-state", ErrorCategory.BUSINESS_RULE),
  EMAIL_INVALID("identity.email-invalid", ErrorCategory.VALIDATION),
  CURRENT_PASSWORD_INVALID("identity.current-password-invalid", ErrorCategory.VALIDATION);

  private final String code;
  private final ErrorCategory category;

  IdentityErrorCode(String code, ErrorCategory category) {
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
