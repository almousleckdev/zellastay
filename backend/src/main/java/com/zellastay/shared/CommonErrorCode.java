package com.zellastay.shared;

public enum CommonErrorCode implements ErrorCode {
  VALIDATION_FAILED("validation.failed", ErrorCategory.VALIDATION),
  REQUEST_MALFORMED("request.malformed", ErrorCategory.VALIDATION),
  REQUEST_NOT_ACCEPTABLE("request.not-acceptable", ErrorCategory.NOT_ACCEPTABLE),
  REQUEST_METHOD_NOT_ALLOWED("request.method-not-allowed", ErrorCategory.METHOD_NOT_ALLOWED),
  REQUEST_UNSUPPORTED_MEDIA_TYPE(
      "request.unsupported-media-type", ErrorCategory.UNSUPPORTED_MEDIA_TYPE),
  REQUEST_TOO_LARGE("request.too-large", ErrorCategory.PAYLOAD_TOO_LARGE),
  RESOURCE_NOT_FOUND("resource.not-found", ErrorCategory.NOT_FOUND),
  AUTH_UNAUTHENTICATED("auth.unauthenticated", ErrorCategory.UNAUTHENTICATED),
  AUTH_FORBIDDEN("auth.forbidden", ErrorCategory.FORBIDDEN),
  DATA_CONFLICT("data.conflict", ErrorCategory.CONFLICT),
  DATA_CONCURRENT_MODIFICATION("data.concurrent-modification", ErrorCategory.CONFLICT),
  PRECONDITION_FAILED("request.precondition-failed", ErrorCategory.PRECONDITION_FAILED),
  RATE_LIMITED("rate.limited", ErrorCategory.RATE_LIMITED),
  SERVICE_BUSY("service.busy", ErrorCategory.UNAVAILABLE),
  INTERNAL_ERROR("internal.error", ErrorCategory.INTERNAL);

  private final String code;
  private final ErrorCategory category;

  CommonErrorCode(String code, ErrorCategory category) {
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
