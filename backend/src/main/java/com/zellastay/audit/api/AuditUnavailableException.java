package com.zellastay.audit.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;
import java.util.List;

/** The audit trail could not be written, so the privileged action must not proceed. */
public class AuditUnavailableException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public AuditUnavailableException(Throwable cause) {
    super(AuditErrorCode.UNAVAILABLE, "audit store unavailable", cause, List.of());
  }
}
