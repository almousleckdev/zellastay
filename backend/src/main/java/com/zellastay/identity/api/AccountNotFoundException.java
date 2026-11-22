package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class AccountNotFoundException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public AccountNotFoundException() {
    this("The account does not exist");
  }

  public AccountNotFoundException(String developerMessage) {
    super(IdentityErrorCode.ACCOUNT_NOT_FOUND, developerMessage);
  }
}
