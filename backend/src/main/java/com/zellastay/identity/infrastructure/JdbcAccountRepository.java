package com.zellastay.identity.infrastructure;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.AccountSnapshot;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.Profile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

  private static final String SELECT =
      """
      select a.*, p.first_name, p.last_name
      from ident_account a join ident_profile p on p.account_id = a.id
      """;

  private final JdbcClient jdbc;

  JdbcAccountRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Account account) {
    AccountSnapshot s = account.snapshot();
    jdbc.sql(
            """
            insert into ident_account (id, kind, email, email_verified_at, password_hash, status,
              locale, failed_login_count, locked_until, password_changed_at, last_login_at,
              created_at, version)
            values (:id, :kind, :email, :verifiedAt, :hash, :status, :locale, :failures,
              :lockedUntil, :passwordChangedAt, :lastLoginAt, :createdAt, 0)
            """)
        .param("id", s.id().value())
        .param("kind", s.kind().name())
        .param("email", s.email().value())
        .param("verifiedAt", Db.timestamp(s.emailVerifiedAt()))
        .param("hash", s.passwordHash())
        .param("status", s.status().name())
        .param("locale", s.locale())
        .param("failures", s.failedLoginCount())
        .param("lockedUntil", Db.timestamp(s.lockedUntil()))
        .param("passwordChangedAt", Db.timestamp(s.passwordChangedAt()))
        .param("lastLoginAt", Db.timestamp(s.lastLoginAt()))
        .param("createdAt", Db.timestamp(s.createdAt()))
        .update();
    jdbc.sql("insert into ident_profile (account_id, first_name, last_name) values (:id, :f, :l)")
        .param("id", s.id().value())
        .param("f", s.profile().firstName())
        .param("l", s.profile().lastName())
        .update();
  }

  @Override
  public void update(Account account) {
    AccountSnapshot s = account.snapshot();
    int rows =
        jdbc.sql(
                """
                update ident_account set email_verified_at = :verifiedAt, password_hash = :hash,
                  status = :status, failed_login_count = :failures, locked_until = :lockedUntil,
                  password_changed_at = :passwordChangedAt, last_login_at = :lastLoginAt,
                  version = version + 1
                where id = :id and version = :version
                """)
            .param("id", s.id().value())
            .param("version", s.version())
            .param("verifiedAt", Db.timestamp(s.emailVerifiedAt()))
            .param("hash", s.passwordHash())
            .param("status", s.status().name())
            .param("failures", s.failedLoginCount())
            .param("lockedUntil", Db.timestamp(s.lockedUntil()))
            .param("passwordChangedAt", Db.timestamp(s.passwordChangedAt()))
            .param("lastLoginAt", Db.timestamp(s.lastLoginAt()))
            .update();
    if (rows == 0) {
      throw new OptimisticLockingFailureException("account was modified concurrently");
    }
  }

  @Override
  public Optional<Account> findById(AccountId id) {
    return jdbc.sql(SELECT + " where a.id = :id")
        .param("id", id.value())
        .query((rs, i) -> map(rs))
        .optional();
  }

  @Override
  public Optional<Account> findByEmail(AccountKind kind, Email email) {
    return jdbc.sql(SELECT + " where a.kind = :kind and a.email = :email")
        .param("kind", kind.name())
        .param("email", email.value())
        .query((rs, i) -> map(rs))
        .optional();
  }

  @Override
  public boolean existsOfKind(AccountKind kind) {
    return jdbc.sql("select exists (select 1 from ident_account where kind = :kind)")
        .param("kind", kind.name())
        .query(Boolean.class)
        .single();
  }

  private static Account map(ResultSet rs) throws SQLException {
    return Account.restore(
        new AccountSnapshot(
            new AccountId(rs.getObject("id", UUID.class)),
            AccountKind.valueOf(rs.getString("kind")),
            new Email(rs.getString("email")),
            Db.instant(rs, "email_verified_at"),
            rs.getString("password_hash"),
            AccountStatus.valueOf(rs.getString("status")),
            new Profile(rs.getString("first_name"), rs.getString("last_name")),
            rs.getString("locale"),
            rs.getInt("failed_login_count"),
            Db.instant(rs, "locked_until"),
            Db.instant(rs, "password_changed_at"),
            Db.instant(rs, "last_login_at"),
            Db.instant(rs, "created_at"),
            rs.getLong("version")));
  }
}
