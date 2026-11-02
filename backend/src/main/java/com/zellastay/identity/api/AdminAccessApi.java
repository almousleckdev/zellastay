package com.zellastay.identity.api;

/**
 * How other modules authorize admin actions: deny unless the admin's roles grant the permission.
 */
public interface AdminAccessApi {

  boolean has(CurrentPrincipal principal, AdminPermission permission);

  /** Throws {@link AdminPermissionDeniedException} unless the principal holds the permission. */
  void require(CurrentPrincipal principal, AdminPermission permission);
}
