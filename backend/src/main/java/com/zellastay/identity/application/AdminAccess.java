package com.zellastay.identity.application;

import com.zellastay.identity.api.AdminAccessApi;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.api.AdminPermissionDeniedException;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.shared.security.TokenAudience;
import org.springframework.stereotype.Service;

/** Admin authorization: an admin session that passed the second factor, holding the permission. */
@Service
class AdminAccess implements AdminAccessApi {

  private final AdminRoleRepository roles;

  AdminAccess(AdminRoleRepository roles) {
    this.roles = roles;
  }

  @Override
  public boolean has(CurrentPrincipal principal, AdminPermission permission) {
    return principal.audience() == TokenAudience.ADMIN
        && principal.mfaVerified()
        && roles.permissionsOf(principal.accountId()).contains(permission);
  }

  @Override
  public void require(CurrentPrincipal principal, AdminPermission permission) {
    if (!has(principal, permission)) {
      throw new AdminPermissionDeniedException(permission);
    }
  }
}
