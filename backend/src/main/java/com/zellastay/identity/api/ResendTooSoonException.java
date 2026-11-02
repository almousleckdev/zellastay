package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class ResendTooSoonException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public ResendTooSoonException() {
    this("A verification message was requested too recently");
  }

  public ResendTooSoonException(String developerMessage) {
    super(IdentityErrorCode.RESEND_TOO_SOON, developerMessage);
  }
}
