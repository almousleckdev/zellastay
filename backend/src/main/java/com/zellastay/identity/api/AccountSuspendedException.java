package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class AccountSuspendedException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public AccountSuspendedException() {
    this("The account is suspended");
  }

  public AccountSuspendedException(String developerMessage) {
    super(IdentityErrorCode.ACCOUNT_SUSPENDED, developerMessage);
  }
}
