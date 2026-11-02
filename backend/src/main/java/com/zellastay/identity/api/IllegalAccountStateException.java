package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class IllegalAccountStateException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public IllegalAccountStateException() {
    this("The account state does not allow this action");
  }

  public IllegalAccountStateException(String developerMessage) {
    super(IdentityErrorCode.ILLEGAL_ACCOUNT_STATE, developerMessage);
  }
}
