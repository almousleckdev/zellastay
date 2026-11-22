package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MfaRepository {

  Optional<MfaEnrollment> find(AccountId accountId);

  /** Stores (or replaces) an unconfirmed enrolment. */
  void savePending(AccountId accountId, String secretCiphertext);

  /** Marks the enrolment confirmed and records the step used. */
  void confirm(AccountId accountId, long step, Instant now);

  /** Records a step; false when a concurrent request already used a step at or after it. */
  boolean advanceStep(AccountId accountId, long step);

  void replaceRecoveryCodes(AccountId accountId, List<String> codeHashes);

  /** Consumes the code; false when it is unknown or already used. */
  boolean consumeRecoveryCode(AccountId accountId, String codeHash, Instant now);
}
