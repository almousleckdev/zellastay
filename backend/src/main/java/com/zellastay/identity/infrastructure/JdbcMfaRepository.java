package com.zellastay.identity.infrastructure;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.domain.MfaEnrollment;
import com.zellastay.identity.domain.MfaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMfaRepository implements MfaRepository {

  private final JdbcClient jdbc;

  JdbcMfaRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<MfaEnrollment> find(AccountId accountId) {
    return jdbc.sql("select * from ident_admin_mfa where account_id = :id")
        .param("id", accountId.value())
        .query(
            (rs, i) ->
                new MfaEnrollment(
                    new AccountId(rs.getObject("account_id", UUID.class)),
                    rs.getString("secret_ciphertext"),
                    Db.instant(rs, "enrolled_at"),
                    rs.getLong("last_used_step")))
        .optional();
  }

  @Override
  public void savePending(AccountId accountId, String secretCiphertext) {
    jdbc.sql(
            """
            insert into ident_admin_mfa (account_id, secret_ciphertext) values (:id, :secret)
            on conflict (account_id) do update set secret_ciphertext = excluded.secret_ciphertext,
              last_used_step = 0
            where ident_admin_mfa.enrolled_at is null
            """)
        .param("id", accountId.value())
        .param("secret", secretCiphertext)
        .update();
  }

  @Override
  public void confirm(AccountId accountId, long step, Instant now) {
    jdbc.sql(
            "update ident_admin_mfa set enrolled_at = :now, last_used_step = :step"
                + " where account_id = :id and enrolled_at is null")
        .param("id", accountId.value())
        .param("now", Db.timestamp(now))
        .param("step", step)
        .update();
  }

  @Override
  public boolean advanceStep(AccountId accountId, long step) {
    return jdbc.sql(
                "update ident_admin_mfa set last_used_step = :step"
                    + " where account_id = :id and last_used_step < :step")
            .param("id", accountId.value())
            .param("step", step)
            .update()
        == 1;
  }

  @Override
  public void replaceRecoveryCodes(AccountId accountId, List<String> codeHashes) {
    jdbc.sql("delete from ident_recovery_code where account_id = :id")
        .param("id", accountId.value())
        .update();
    for (String hash : codeHashes) {
      jdbc.sql("insert into ident_recovery_code (account_id, code_hash) values (:id, :hash)")
          .param("id", accountId.value())
          .param("hash", hash)
          .update();
    }
  }

  @Override
  public boolean consumeRecoveryCode(AccountId accountId, String codeHash, Instant now) {
    return jdbc.sql(
                "update ident_recovery_code set used_at = :now"
                    + " where account_id = :id and code_hash = :hash and used_at is null")
            .param("id", accountId.value())
            .param("hash", codeHash)
            .param("now", Db.timestamp(now))
            .update()
        == 1;
  }
}
