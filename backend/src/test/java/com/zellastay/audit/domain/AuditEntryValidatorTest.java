package com.zellastay.audit.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.audit.TestAuditAction;
import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditEntryInvalidException;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditSubject;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryValidatorTest {

  private final AuditEntryValidator validator = new AuditEntryValidator();

  private static AuditEntry entry(TestAuditAction action, Map<String, Object> details) {
    return new AuditEntry(
        AuditActor.admin(UUID.randomUUID()),
        action,
        new AuditSubject("account", UUID.randomUUID().toString()),
        AuditOutcome.SUCCESS,
        details);
  }

  @Test
  void acceptsDeclaredScalarDetails() {
    assertThatCode(
            () ->
                validator.validate(
                    entry(
                        TestAuditAction.LOGIN_SUCCESS, Map.of("method", "password", "attempt", 2))))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsEntriesWithoutDetails() {
    assertThatCode(() -> validator.validate(entry(TestAuditAction.LOGIN_SUCCESS, Map.of())))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDetailKeysTheActionDoesNotDeclare() {
    assertThatThrownBy(
            () -> validator.validate(entry(TestAuditAction.LOGIN_SUCCESS, Map.of("extra", "x"))))
        .isInstanceOf(AuditEntryInvalidException.class)
        .hasMessageContaining("extra");
  }

  @Test
  void rejectsActionsThatDeclareSecretLookingKeys() {
    assertThatThrownBy(
            () ->
                validator.validate(entry(TestAuditAction.DECLARES_SECRET, Map.of("password", "x"))))
        .isInstanceOf(AuditEntryInvalidException.class);
  }

  @Test
  void rejectsMalformedActionCodes() {
    assertThatThrownBy(() -> validator.validate(entry(TestAuditAction.BAD_CODE_FORMAT, Map.of())))
        .isInstanceOf(AuditEntryInvalidException.class);
  }

  @Test
  void rejectsUnmaskedEmailAddressesInValues() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    entry(TestAuditAction.ROLE_GRANTED, Map.of("role", "person@example.com"))))
        .isInstanceOf(AuditEntryInvalidException.class);
  }

  @Test
  void acceptsMaskedEmailAddresses() {
    assertThatCode(
            () ->
                validator.validate(
                    entry(TestAuditAction.ROLE_GRANTED, Map.of("role", "p***@e***.com"))))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsTokenLookingValues() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    entry(
                        TestAuditAction.ROLE_GRANTED,
                        Map.of("role", "eyJhbGciOiJFUzI1NiJ9.e30.sig"))))
        .isInstanceOf(AuditEntryInvalidException.class);
  }

  @Test
  void rejectsOverlongAndNonScalarValues() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    entry(TestAuditAction.ROLE_GRANTED, Map.of("role", "x".repeat(201)))))
        .isInstanceOf(AuditEntryInvalidException.class);
    assertThatThrownBy(
            () ->
                validator.validate(
                    entry(TestAuditAction.ROLE_GRANTED, Map.of("role", java.util.List.of("a")))))
        .isInstanceOf(AuditEntryInvalidException.class);
  }

  @Test
  void rejectsAnonymousAndSystemActorsCarryingAnAccountId() {
    assertThatThrownBy(
            () -> new AuditActor(com.zellastay.audit.api.ActorType.SYSTEM, UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AuditActor(com.zellastay.audit.api.ActorType.ADMIN, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
