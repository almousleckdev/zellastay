package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class MfaInvalidException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public MfaInvalidException() {
    this("The second-factor code is wrong");
  }

  public MfaInvalidException(String developerMessage) {
    super(IdentityErrorCode.MFA_INVALID, developerMessage);
  }
}
