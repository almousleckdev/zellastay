package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class VerificationAttemptsExceededException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public VerificationAttemptsExceededException() {
    this("Too many wrong verification attempts");
  }

  public VerificationAttemptsExceededException(String developerMessage) {
    super(IdentityErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED, developerMessage);
  }
}
