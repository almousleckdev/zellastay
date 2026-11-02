package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class InvalidCredentialsException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidCredentialsException() {
    this("The email or password is incorrect");
  }

  public InvalidCredentialsException(String developerMessage) {
    super(IdentityErrorCode.INVALID_CREDENTIALS, developerMessage);
  }
}
