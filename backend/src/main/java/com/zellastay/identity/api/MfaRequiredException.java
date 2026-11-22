package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class MfaRequiredException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public MfaRequiredException() {
    this("A second factor is required");
  }

  public MfaRequiredException(String developerMessage) {
    super(IdentityErrorCode.MFA_REQUIRED, developerMessage);
  }
}
