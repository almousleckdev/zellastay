package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.api.AdminRole;
import java.time.Instant;
import java.util.Set;

public interface AdminRoleRepository {

  Set<AdminRole> rolesOf(AccountId accountId);

  Set<AdminPermission> permissionsOf(AccountId accountId);

  /** Returns false when the account already holds the role. */
  boolean grant(AccountId accountId, AdminRole role, Instant now);

  /** Returns false when the account did not hold the role. */
  boolean revoke(AccountId accountId, AdminRole role);

  int countHolders(AdminRole role);
}
