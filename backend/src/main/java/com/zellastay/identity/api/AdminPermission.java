package com.zellastay.identity.api;

/** Fine-grained admin permissions. Other modules add theirs here when they need them. */
public enum AdminPermission {
  ACCOUNT_SUSPEND("account.suspend"),
  ACCOUNT_REINSTATE("account.reinstate"),
  SESSION_REVOKE("session.revoke"),
  AUDIT_READ("audit.read"),
  ADMIN_MANAGE("admin.manage");

  private final String code;

  AdminPermission(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static AdminPermission fromCode(String code) {
    for (AdminPermission permission : values()) {
      if (permission.code.equals(code)) {
        return permission;
      }
    }
    throw new IllegalArgumentException("unknown permission " + code);
  }
}
