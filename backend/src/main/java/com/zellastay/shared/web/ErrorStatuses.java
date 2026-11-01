package com.zellastay.shared.web;

import com.zellastay.shared.ErrorCategory;
import org.springframework.http.HttpStatus;

final class ErrorStatuses {

  private ErrorStatuses() {}

  static HttpStatus statusOf(ErrorCategory category) {
    return switch (category) {
      case VALIDATION -> HttpStatus.BAD_REQUEST;
      case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
      case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
      case CONFLICT -> HttpStatus.CONFLICT;
      case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
      case PAYLOAD_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
      case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
      case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT;
      case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
      case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }
}
