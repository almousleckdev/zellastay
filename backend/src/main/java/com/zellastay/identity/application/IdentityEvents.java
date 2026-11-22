package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.events.AccountDeactivated;
import com.zellastay.identity.api.events.AccountRegistered;
import com.zellastay.identity.api.events.AccountReinstated;
import com.zellastay.identity.api.events.AccountSuspended;
import com.zellastay.identity.api.events.PasswordResetRequested;
import com.zellastay.identity.api.events.VerificationCodeRequested;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Builds and publishes identity events with the common envelope. Payloads carry no secrets. */
@Component
class IdentityEvents {

  private static final int SCHEMA_VERSION = 1;

  private final ApplicationEventPublisher publisher;
  private final Clock clock;

  IdentityEvents(ApplicationEventPublisher publisher, Clock clock) {
    this.publisher = publisher;
    this.clock = clock;
  }

  void registered(UUID accountId, AccountKind kind, String locale) {
    publisher.publishEvent(
        new AccountRegistered(
            id(), clock.instant(), SCHEMA_VERSION, correlation(), accountId, kind, locale));
  }

  void verificationRequested(UUID accountId, String purpose) {
    publisher.publishEvent(
        new VerificationCodeRequested(
            id(), clock.instant(), SCHEMA_VERSION, correlation(), accountId, purpose, "EMAIL"));
  }

  void passwordResetRequested(UUID accountId) {
    publisher.publishEvent(
        new PasswordResetRequested(
            id(), clock.instant(), SCHEMA_VERSION, correlation(), accountId));
  }

  void suspended(UUID accountId) {
    publisher.publishEvent(
        new AccountSuspended(id(), clock.instant(), SCHEMA_VERSION, correlation(), accountId));
  }

  void reinstated(UUID accountId) {
    publisher.publishEvent(
        new AccountReinstated(id(), clock.instant(), SCHEMA_VERSION, correlation(), accountId));
  }

  void deactivated(UUID accountId) {
    publisher.publishEvent(
        new AccountDeactivated(id(), clock.instant(), SCHEMA_VERSION, correlation(), accountId));
  }

  private static UUID id() {
    return UUID.randomUUID();
  }

  private static String correlation() {
    return MDC.get("requestId");
  }
}
