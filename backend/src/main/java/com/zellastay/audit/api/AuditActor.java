package com.zellastay.audit.api;

import java.util.Objects;
import java.util.UUID;

/** Who performed the action; {@code organizationId} is the organization acted for, if any. */
public record AuditActor(ActorType type, UUID id, UUID organizationId) {

  public AuditActor {
    Objects.requireNonNull(type, "type");
    if (type.isAccount() && id == null) {
      throw new IllegalArgumentException("an account actor needs an id");
    }
    if (!type.isAccount() && (id != null || organizationId != null)) {
      throw new IllegalArgumentException("system and anonymous actors carry no ids");
    }
  }

  public static AuditActor customer(UUID accountId) {
    return new AuditActor(ActorType.CUSTOMER, accountId, null);
  }

  public static AuditActor vendorUser(UUID accountId, UUID organizationId) {
    return new AuditActor(ActorType.VENDOR_USER, accountId, organizationId);
  }

  public static AuditActor admin(UUID accountId) {
    return new AuditActor(ActorType.ADMIN, accountId, null);
  }

  public static AuditActor system() {
    return new AuditActor(ActorType.SYSTEM, null, null);
  }

  public static AuditActor anonymous() {
    return new AuditActor(ActorType.ANONYMOUS, null, null);
  }
}
