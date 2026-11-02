package com.zellastay.identity.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived cache of "is this session still usable"; a miss or an outage is never an approval.
 */
public interface SessionStatusCache {

  Optional<Boolean> get(UUID sessionId);

  void put(UUID sessionId, boolean active);

  void evict(UUID sessionId);
}
