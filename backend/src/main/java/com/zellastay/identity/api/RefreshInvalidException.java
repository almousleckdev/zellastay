package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class RefreshInvalidException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public RefreshInvalidException() {
    this("The refresh token is invalid");
  }

  public RefreshInvalidException(String developerMessage) {
    super(IdentityErrorCode.REFRESH_INVALID, developerMessage);
  }
}
