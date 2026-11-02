package com.zellastay.identity.infrastructure;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AdminPermission;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.domain.AdminRoleRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAdminRoleRepository implements AdminRoleRepository {

  private final JdbcClient jdbc;

  JdbcAdminRoleRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Set<AdminRole> rolesOf(AccountId accountId) {
    Set<AdminRole> roles = EnumSet.noneOf(AdminRole.class);
    jdbc.sql("select role from ident_admin_role where account_id = :id")
        .param("id", accountId.value())
        .query(String.class)
        .list()
        .forEach(role -> roles.add(AdminRole.valueOf(role)));
    return roles;
  }

  @Override
  public Set<AdminPermission> permissionsOf(AccountId accountId) {
    Set<AdminPermission> permissions = EnumSet.noneOf(AdminPermission.class);
    jdbc.sql(
            """
            select distinct p.permission from ident_admin_role r
            join ident_admin_role_permission p on p.role = r.role
            where r.account_id = :id
            """)
        .param("id", accountId.value())
        .query(String.class)
        .list()
        .forEach(code -> permissions.add(AdminPermission.fromCode(code)));
    return permissions;
  }

  @Override
  public boolean grant(AccountId accountId, AdminRole role, Instant now) {
    return jdbc.sql(
                "insert into ident_admin_role (account_id, role, granted_at) values (:id, :role, :now)"
                    + " on conflict do nothing")
            .param("id", accountId.value())
            .param("role", role.name())
            .param("now", Db.timestamp(now))
            .update()
        == 1;
  }

  @Override
  public boolean revoke(AccountId accountId, AdminRole role) {
    return jdbc.sql("delete from ident_admin_role where account_id = :id and role = :role")
            .param("id", accountId.value())
            .param("role", role.name())
            .update()
        == 1;
  }

  @Override
  public int countHolders(AdminRole role) {
    return jdbc.sql("select count(*) from ident_admin_role where role = :role")
        .param("role", role.name())
        .query(Integer.class)
        .single();
  }
}
