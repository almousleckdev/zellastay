package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class CurrentPasswordInvalidException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public CurrentPasswordInvalidException() {
    super(IdentityErrorCode.CURRENT_PASSWORD_INVALID, "The current password is wrong");
  }
}
