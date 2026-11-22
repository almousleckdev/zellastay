package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class ResetTokenInvalidException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public ResetTokenInvalidException() {
    this("The reset link is invalid or expired");
  }

  public ResetTokenInvalidException(String developerMessage) {
    super(IdentityErrorCode.RESET_TOKEN_INVALID, developerMessage);
  }
}
