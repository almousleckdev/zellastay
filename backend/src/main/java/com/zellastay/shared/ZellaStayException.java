package com.zellastay.shared;

import java.io.Serial;
import java.util.List;

public abstract class ZellaStayException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient ErrorCode errorCode;
  private final String[] messageArguments;

  protected ZellaStayException(ErrorCode errorCode, String developerMessage) {
    this(errorCode, developerMessage, null, List.of());
  }

  protected ZellaStayException(
      ErrorCode errorCode,
      String developerMessage,
      Throwable cause,
      List<String> messageArguments) {
    super(developerMessage, cause);
    this.errorCode = errorCode;
    this.messageArguments = messageArguments.toArray(String[]::new);
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public List<String> messageArguments() {
    return List.of(messageArguments);
  }
}
