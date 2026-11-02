package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.identity.domain.Account;

/** Maps identity concepts onto the audit module's actor and subject types. */
final class AuditActors {

  private AuditActors() {}

  static AuditActor actor(Account account) {
    return switch (account.snapshot().kind()) {
      case CUSTOMER -> AuditActor.customer(account.id().value());
      case VENDOR_USER -> AuditActor.vendorUser(account.id().value(), null);
      case ADMIN -> AuditActor.admin(account.id().value());
    };
  }

  static AuditSubject subject(Account account) {
    return new AuditSubject("account", account.id().value().toString());
  }
}
