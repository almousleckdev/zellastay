package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class VerificationInvalidException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public VerificationInvalidException() {
    this("The verification code is wrong or expired");
  }

  public VerificationInvalidException(String developerMessage) {
    super(IdentityErrorCode.VERIFICATION_INVALID, developerMessage);
  }
}
