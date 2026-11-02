package com.zellastay.identity.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class SessionNotFoundException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public SessionNotFoundException() {
    this("The session does not exist");
  }

  public SessionNotFoundException(String developerMessage) {
    super(IdentityErrorCode.SESSION_NOT_FOUND, developerMessage);
  }
}
