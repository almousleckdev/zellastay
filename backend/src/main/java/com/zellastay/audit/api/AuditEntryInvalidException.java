package com.zellastay.audit.api;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

/** The entry breaks the audit rules (undeclared key, secret-looking data). A programming error. */
public class AuditEntryInvalidException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public AuditEntryInvalidException(String developerMessage) {
    super(AuditErrorCode.ENTRY_INVALID, developerMessage);
  }
}
