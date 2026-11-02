package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class PasswordRejectedException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public PasswordRejectedException() {
    this("The password does not meet the policy");
  }

  public PasswordRejectedException(String developerMessage) {
    super(IdentityErrorCode.PASSWORD_REJECTED, developerMessage);
  }
}
