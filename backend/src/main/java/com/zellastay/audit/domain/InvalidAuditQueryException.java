package com.zellastay.audit.domain;

import com.zellastay.audit.api.AuditErrorCode;
import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class InvalidAuditQueryException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidAuditQueryException(String developerMessage) {
    super(AuditErrorCode.QUERY_INVALID, developerMessage);
  }
}
