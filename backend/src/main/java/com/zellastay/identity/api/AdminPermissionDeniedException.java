package com.zellastay.identity.api;

import com.zellastay.shared.CommonErrorCode;
import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class AdminPermissionDeniedException extends ZellaStayException {

  @Serial private static final long serialVersionUID = 1L;

  public AdminPermissionDeniedException(AdminPermission permission) {
    super(CommonErrorCode.AUTH_FORBIDDEN, "missing admin permission " + permission.code());
  }
}
