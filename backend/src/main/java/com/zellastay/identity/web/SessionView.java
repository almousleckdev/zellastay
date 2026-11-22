package com.zellastay.identity.web;

import com.zellastay.identity.application.SessionSummary;
import java.time.Instant;
import java.util.UUID;

record SessionView(UUID id, Instant createdAt, Instant lastUsedAt, boolean current) {

  static SessionView from(SessionSummary view) {
    return new SessionView(view.id(), view.createdAt(), view.lastUsedAt(), view.current());
  }
}
