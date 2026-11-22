package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditAction;
import java.util.Set;

/** Every audited identity action; detail keys are the only data an entry may carry. */
public enum IdentityAuditAction implements AuditAction {
  ACCOUNT_REGISTERED("account.registered", Set.of("kind")),
  REGISTRATION_DUPLICATE("account.registration-duplicate", Set.of("kind")),
  EMAIL_VERIFIED("account.email-verified", Set.of()),
  LOGIN_SUCCESS("auth.login.success", Set.of("audience")),
  LOGIN_FAILURE("auth.login.failure", Set.of("audience", "reason")),
  LOGIN_LOCKOUT("auth.login.lockout", Set.of("audience")),
  LOGOUT("auth.logout", Set.of()),
  SESSION_REVOKED("auth.session.revoked", Set.of("reason")),
  REFRESH_REUSE_DETECTED("auth.refresh.reuse-detected", Set.of()),
  PASSWORD_CHANGED("account.password-changed", Set.of()),
  PASSWORD_RESET_REQUESTED("account.password-reset-requested", Set.of()),
  PASSWORD_RESET_COMPLETED("account.password-reset-completed", Set.of()),
  ACCOUNT_SUSPENDED("account.suspended", Set.of("reasonCode")),
  ACCOUNT_REINSTATED("account.reinstated", Set.of()),
  ACCOUNT_DEACTIVATED("account.deactivated", Set.of()),
  ADMIN_MFA_ENROLLED("admin.mfa.enrolled", Set.of()),
  ADMIN_MFA_VERIFIED("admin.mfa.verified", Set.of()),
  ADMIN_MFA_FAILED("admin.mfa.failed", Set.of()),
  ADMIN_RECOVERY_CODE_USED("admin.mfa.recovery-used", Set.of()),
  ADMIN_ACCOUNT_CREATED("admin.account.created", Set.of("role")),
  ADMIN_ROLE_GRANTED("admin.role.granted", Set.of("role")),
  ADMIN_ROLE_REVOKED("admin.role.revoked", Set.of("role")),
  ADMIN_BOOTSTRAP_EXECUTED("admin.bootstrap.executed", Set.of());

  private final String code;
  private final Set<String> detailKeys;

  IdentityAuditAction(String code, Set<String> detailKeys) {
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
