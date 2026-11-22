package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.domain.Account;

/** Maps identity concepts onto the audit module's actor and subject types. */
final class AuditActors {

  private AuditActors() {}

  static AuditActor actor(Account account) {
    return actor(account.snapshot().kind(), account.id());
  }

  static AuditActor actor(CurrentPrincipal principal) {
    return actor(principal.kind(), principal.accountId());
  }

  static AuditSubject subject(Account account) {
    return subject(account.id());
  }

  /** The subject of an event about an account that could not be identified. */
  static AuditSubject unknownAccount() {
    return new AuditSubject("account", null);
  }

  static AuditSubject subject(AccountId id) {
    return new AuditSubject("account", id.value().toString());
  }

  private static AuditActor actor(AccountKind kind, AccountId id) {
    return switch (kind) {
      case CUSTOMER -> AuditActor.customer(id.value());
      case VENDOR_USER -> AuditActor.vendorUser(id.value(), null);
      case ADMIN -> AuditActor.admin(id.value());
    };
  }
}
