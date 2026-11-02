package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.domain.AdminRoleRepository;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Read access to what an admin may do, for building the request's authorities. */
@Service
public class AdminPermissions {

  private final AdminRoleRepository roles;

  AdminPermissions(AdminRoleRepository roles) {
    this.roles = roles;
  }

  public Set<AdminPermission> of(AccountId accountId) {
    return roles.permissionsOf(accountId);
  }
}
