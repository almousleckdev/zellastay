package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class EmailInvalidException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public EmailInvalidException() {
    this("The email address is not valid");
  }

  public EmailInvalidException(String developerMessage) {
    super(IdentityErrorCode.EMAIL_INVALID, developerMessage);
  }
}
