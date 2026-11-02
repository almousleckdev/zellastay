package com.zellastay.audit.api;

import java.util.Objects;

/** What the action was about; {@code id} is a typed identifier rendered as text. */
public record AuditSubject(String type, String id) {

  public AuditSubject {
    Objects.requireNonNull(type, "type");
  }
}
