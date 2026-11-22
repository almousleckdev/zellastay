package com.zellastay.identity.infrastructure;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionRepository;
import com.zellastay.shared.security.TokenAudience;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSessionRepository implements SessionRepository {

  private final JdbcClient jdbc;

  JdbcSessionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Session s) {
    jdbc.sql(
            """
            insert into ident_session (id, account_id, audience, mfa_verified, created_at,
              absolute_expires_at, idle_timeout_seconds, last_used_at, revoked_at, revoked_reason)
            values (:id, :account, :audience, :mfa, :createdAt, :absolute, :idle, :lastUsed,
              :revokedAt, :reason)
            """)
        .param("id", s.id())
        .param("account", s.accountId().value())
        .param("audience", s.audience().name())
        .param("mfa", s.mfaVerified())
        .param("createdAt", Db.timestamp(s.createdAt()))
        .param("absolute", Db.timestamp(s.absoluteExpiresAt()))
        .param("idle", s.idleTimeout().toSeconds())
        .param("lastUsed", Db.timestamp(s.lastUsedAt()))
        .param("revokedAt", Db.timestamp(s.revokedAt()))
        .param("reason", s.revokedReason())
        .update();
  }

  @Override
  public void update(Session s) {
    jdbc.sql(
            """
            update ident_session set last_used_at = :lastUsed, revoked_at = :revokedAt,
              revoked_reason = :reason where id = :id
            """)
        .param("id", s.id())
        .param("lastUsed", Db.timestamp(s.lastUsedAt()))
        .param("revokedAt", Db.timestamp(s.revokedAt()))
        .param("reason", s.revokedReason())
        .update();
  }

  @Override
  public Optional<Session> findById(UUID id) {
    return jdbc.sql("select * from ident_session where id = :id")
        .param("id", id)
        .query((rs, i) -> map(rs))
        .optional();
  }

  @Override
  public List<Session> findActiveByAccount(AccountId accountId, Instant now) {
    return jdbc.sql(
            """
            select * from ident_session
            where account_id = :account and revoked_at is null and absolute_expires_at > :now
              and last_used_at + make_interval(secs => idle_timeout_seconds) > :now
            order by last_used_at desc
            """)
        .param("account", accountId.value())
        .param("now", Db.timestamp(now))
        .query((rs, i) -> map(rs))
        .list();
  }

  @Override
  public void revokeAllForAccount(AccountId accountId, String reason, Instant now) {
    revokeAllForAccountExcept(accountId, null, reason, now);
  }

  @Override
  public void revokeAllForAccountExcept(
      AccountId accountId, UUID keep, String reason, Instant now) {
    jdbc.sql(
            """
            update ident_session set revoked_at = :now, revoked_reason = :reason
            where account_id = :account and revoked_at is null
              and (cast(:keep as uuid) is null or id <> cast(:keep as uuid))
            """)
        .param("account", accountId.value())
        .param("keep", keep)
        .param("reason", reason)
        .param("now", Db.timestamp(now))
        .update();
  }

  private static Session map(ResultSet rs) throws SQLException {
    return Session.restore(
        rs.getObject("id", UUID.class),
        new AccountId(rs.getObject("account_id", UUID.class)),
        TokenAudience.valueOf(rs.getString("audience")),
        rs.getBoolean("mfa_verified"),
        Db.instant(rs, "created_at"),
        Db.instant(rs, "absolute_expires_at"),
        Duration.ofSeconds(rs.getLong("idle_timeout_seconds")),
        Db.instant(rs, "last_used_at"),
        Db.instant(rs, "revoked_at"),
        rs.getString("revoked_reason"));
  }
}
