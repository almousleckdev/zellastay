package com.zellastay.identity.infrastructure;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.VerificationChallenge;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcChallengeRepository implements ChallengeRepository {

  private final JdbcClient jdbc;

  JdbcChallengeRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(VerificationChallenge c) {
    jdbc.sql(
            """
            insert into ident_challenge (id, account_id, purpose, secret_hash, expires_at,
              attempts, consumed_at, created_at)
            values (:id, :account, :purpose, :hash, :expiresAt, :attempts, :consumedAt, :createdAt)
            """)
        .param("id", c.id())
        .param("account", c.accountId().value())
        .param("purpose", c.purpose().name())
        .param("hash", c.secretHash())
        .param("expiresAt", Db.timestamp(c.expiresAt()))
        .param("attempts", c.attempts())
        .param("consumedAt", Db.timestamp(c.consumedAt()))
        .param("createdAt", Db.timestamp(c.createdAt()))
        .update();
  }

  @Override
  public void update(VerificationChallenge c) {
    jdbc.sql(
            "update ident_challenge set attempts = :attempts, consumed_at = :consumedAt where id = :id")
        .param("id", c.id())
        .param("attempts", c.attempts())
        .param("consumedAt", Db.timestamp(c.consumedAt()))
        .update();
  }

  @Override
  public Optional<VerificationChallenge> findById(UUID id) {
    return jdbc.sql("select * from ident_challenge where id = :id")
        .param("id", id)
        .query((rs, i) -> map(rs))
        .optional();
  }

  @Override
  public Optional<VerificationChallenge> findLatest(AccountId accountId, ChallengePurpose purpose) {
    return jdbc.sql(
            "select * from ident_challenge where account_id = :account and purpose = :purpose"
                + " order by created_at desc limit 1")
        .param("account", accountId.value())
        .param("purpose", purpose.name())
        .query((rs, i) -> map(rs))
        .optional();
  }

  @Override
  public int countIssuedSince(AccountId accountId, ChallengePurpose purpose, Instant since) {
    return jdbc.sql(
            "select count(*) from ident_challenge where account_id = :account"
                + " and purpose = :purpose and created_at >= :since")
        .param("account", accountId.value())
        .param("purpose", purpose.name())
        .param("since", Db.timestamp(since))
        .query(Integer.class)
        .single();
  }

  @Override
  public Optional<Instant> lastIssuedAt(AccountId accountId, ChallengePurpose purpose) {
    return findLatest(accountId, purpose).map(VerificationChallenge::createdAt);
  }

  private static VerificationChallenge map(ResultSet rs) throws SQLException {
    return VerificationChallenge.restore(
        rs.getObject("id", UUID.class),
        new AccountId(rs.getObject("account_id", UUID.class)),
        ChallengePurpose.valueOf(rs.getString("purpose")),
        rs.getString("secret_hash"),
        Db.instant(rs, "expires_at"),
        Db.instant(rs, "created_at"),
        rs.getInt("attempts"),
        Db.instant(rs, "consumed_at"));
  }
}
