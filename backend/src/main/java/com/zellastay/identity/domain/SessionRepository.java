package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

  void insert(Session session);

  void update(Session session);

  Optional<Session> findById(UUID id);

  List<Session> findActiveByAccount(AccountId accountId, Instant now);

  /** Revokes every non-revoked session of the account except the optional one to keep. */
  void revokeAllForAccount(AccountId accountId, String reason, Instant now);

  void revokeAllForAccountExcept(AccountId accountId, UUID keep, String reason, Instant now);
}
