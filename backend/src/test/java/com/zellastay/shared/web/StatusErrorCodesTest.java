package com.zellastay.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.shared.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;

class StatusErrorCodesTest {

  @Test
  void standardStatusesMapToTheirDedicatedCodes() {
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(404)))
        .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(405)))
        .isEqualTo(CommonErrorCode.REQUEST_METHOD_NOT_ALLOWED);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(406)))
        .isEqualTo(CommonErrorCode.REQUEST_NOT_ACCEPTABLE);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(412)))
        .isEqualTo(CommonErrorCode.PRECONDITION_FAILED);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(413)))
        .isEqualTo(CommonErrorCode.REQUEST_TOO_LARGE);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(415)))
        .isEqualTo(CommonErrorCode.REQUEST_UNSUPPORTED_MEDIA_TYPE);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(429)))
        .isEqualTo(CommonErrorCode.RATE_LIMITED);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(503)))
        .isEqualTo(CommonErrorCode.SERVICE_BUSY);
  }

  @Test
  void otherClientErrorsAreMalformedRequests() {
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(400)))
        .isEqualTo(CommonErrorCode.REQUEST_MALFORMED);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(418)))
        .isEqualTo(CommonErrorCode.REQUEST_MALFORMED);
  }

  @Test
  void otherServerErrorsAreInternalErrors() {
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(500)))
        .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
    assertThat(StatusErrorCodes.codeFor(HttpStatusCode.valueOf(502)))
        .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
  }
}
