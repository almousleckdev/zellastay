package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeRepository {

  void insert(VerificationChallenge challenge);

  void update(VerificationChallenge challenge);

  Optional<VerificationChallenge> findById(UUID id);

  Optional<VerificationChallenge> findLatest(AccountId accountId, ChallengePurpose purpose);

  int countIssuedSince(AccountId accountId, ChallengePurpose purpose, Instant since);

  Optional<Instant> lastIssuedAt(AccountId accountId, ChallengePurpose purpose);
}
