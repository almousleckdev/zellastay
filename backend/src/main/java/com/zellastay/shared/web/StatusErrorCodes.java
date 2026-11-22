package com.zellastay.shared.web;

import com.zellastay.shared.CommonErrorCode;
import org.springframework.http.HttpStatusCode;

/** The error code that describes a bare HTTP status, for failures raised by the framework. */
final class StatusErrorCodes {

  private StatusErrorCodes() {}

  static CommonErrorCode codeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
      case 405 -> CommonErrorCode.REQUEST_METHOD_NOT_ALLOWED;
      case 406 -> CommonErrorCode.REQUEST_NOT_ACCEPTABLE;
      case 412 -> CommonErrorCode.PRECONDITION_FAILED;
      case 413 -> CommonErrorCode.REQUEST_TOO_LARGE;
      case 415 -> CommonErrorCode.REQUEST_UNSUPPORTED_MEDIA_TYPE;
      case 429 -> CommonErrorCode.RATE_LIMITED;
      case 503 -> CommonErrorCode.SERVICE_BUSY;
      default ->
          status.is5xxServerError()
              ? CommonErrorCode.INTERNAL_ERROR
              : CommonErrorCode.REQUEST_MALFORMED;
    };
  }
}
