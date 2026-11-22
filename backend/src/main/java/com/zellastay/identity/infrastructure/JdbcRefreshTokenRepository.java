package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.RefreshToken;
import com.zellastay.identity.domain.RefreshTokenRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRefreshTokenRepository implements RefreshTokenRepository {

  private final JdbcClient jdbc;

  JdbcRefreshTokenRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(RefreshToken t) {
    jdbc.sql(
            """
            insert into ident_refresh_token (id, session_id, token_hash, issued_at, used_at, replaced_by)
            values (:id, :session, :hash, :issuedAt, :usedAt, :replacedBy)
            """)
        .param("id", t.id())
        .param("session", t.sessionId())
        .param("hash", t.tokenHash())
        .param("issuedAt", Db.timestamp(t.issuedAt()))
        .param("usedAt", Db.timestamp(t.usedAt()))
        .param("replacedBy", t.replacedBy())
        .update();
  }

  @Override
  public Optional<RefreshToken> findByHash(String tokenHash) {
    return jdbc.sql("select * from ident_refresh_token where token_hash = :hash")
        .param("hash", tokenHash)
        .query((rs, i) -> map(rs))
        .optional();
  }

  @Override
  public Optional<RefreshToken> findById(UUID id) {
    return jdbc.sql("select * from ident_refresh_token where id = :id")
        .param("id", id)
        .query((rs, i) -> map(rs))
        .optional();
  }

  @Override
  public boolean markUsed(UUID id, Instant usedAt, UUID replacedBy) {
    return jdbc.sql(
                "update ident_refresh_token set used_at = :usedAt, replaced_by = :replacedBy"
                    + " where id = :id and used_at is null")
            .param("id", id)
            .param("usedAt", Db.timestamp(usedAt))
            .param("replacedBy", replacedBy)
            .update()
        == 1;
  }

  private static RefreshToken map(ResultSet rs) throws SQLException {
    return new RefreshToken(
        rs.getObject("id", UUID.class),
        rs.getObject("session_id", UUID.class),
        rs.getString("token_hash"),
        Db.instant(rs, "issued_at"),
        Db.instant(rs, "used_at"),
        rs.getObject("replaced_by", UUID.class));
  }
}
