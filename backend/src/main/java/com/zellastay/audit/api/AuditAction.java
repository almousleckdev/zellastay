package com.zellastay.audit.api;

import java.util.Set;

/**
 * An auditable action. Each module declares its actions as an enum implementing this interface, so
 * the audit module stays independent of every other module.
 */
public interface AuditAction {

  /** Stable dotted code such as {@code auth.login.success}. */
  String code();

  /** The only detail keys an entry for this action may carry. */
  Set<String> detailKeys();
}
