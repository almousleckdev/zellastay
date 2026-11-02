package com.zellastay.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

  void insert(RefreshToken token);

  Optional<RefreshToken> findByHash(String tokenHash);

  Optional<RefreshToken> findById(UUID id);

  /** Marks the token used; returns false when it was already used (lost a race). */
  boolean markUsed(UUID id, Instant usedAt, UUID replacedBy);
}
