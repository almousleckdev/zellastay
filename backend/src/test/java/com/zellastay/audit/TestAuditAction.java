package com.zellastay.audit;

import com.zellastay.audit.api.AuditAction;
import java.util.Set;

public enum TestAuditAction implements AuditAction {
  LOGIN_SUCCESS("auth.login.success", Set.of("method", "attempt")),
  ROLE_GRANTED("admin.role.granted", Set.of("role")),
  DECLARES_SECRET("test.declares.secret", Set.of("password")),
  BAD_CODE_FORMAT("Bad Code", Set.of());

  private final String code;
  private final Set<String> detailKeys;

  TestAuditAction(String code, Set<String> detailKeys) {
    this.code = code;
    this.detailKeys = detailKeys;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public Set<String> detailKeys() {
    return detailKeys;
  }
}
