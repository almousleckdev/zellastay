package com.zellastay.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.api.PasswordRejectedException;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.AdminRoleRepository;
import com.zellastay.identity.domain.PasswordHasher;
import com.zellastay.identity.domain.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

class AdminBootstrapServiceTest {

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final AdminRoleRepository roles = mock(AdminRoleRepository.class);
  private final PasswordHasher hasher = mock(PasswordHasher.class);
  private final AuditRecorder audit = mock(AuditRecorder.class);
  private final TransactionTemplate transaction =
      new TransactionTemplate(
          new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
              return new Object();
            }

            @Override
            protected void doBegin(
                Object transaction,
                org.springframework.transaction.TransactionDefinition definition) {}

            @Override
            protected void doCommit(
                org.springframework.transaction.support.DefaultTransactionStatus status) {}

            @Override
            protected void doRollback(
                org.springframework.transaction.support.DefaultTransactionStatus status) {}
          });
  private final AdminBootstrapService service =
      new AdminBootstrapService(
          accounts,
          roles,
          hasher,
          new PasswordPolicy(Set.of("password123")),
          new IdentityAuditTrail(audit),
          transaction,
          Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC));

  @Test
  void createsTheFirstSuperAdminWhenNoAdministratorExists() {
    when(accounts.existsOfKind(AccountKind.ADMIN)).thenReturn(false);
    when(hasher.hash("Str0ng-Bootstrap-pass")).thenReturn("hashed");

    AdminBootstrapResult result = service.bootstrap("First@Example.com", "Str0ng-Bootstrap-pass");

    assertThat(result).isEqualTo(AdminBootstrapResult.CREATED);
    ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
    verify(accounts).insert(account.capture());
    assertThat(account.getValue().snapshot().email().value()).isEqualTo("first@example.com");
    assertThat(account.getValue().snapshot().kind()).isEqualTo(AccountKind.ADMIN);
    assertThat(account.getValue().snapshot().passwordHash()).isEqualTo("hashed");
    verify(roles)
        .grant(
            account.getValue().id(), AdminRole.SUPER_ADMIN, Instant.parse("2026-11-02T10:00:00Z"));
    ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
    verify(audit).record(entry.capture());
    assertThat(entry.getValue().action().code()).isEqualTo("admin.bootstrap.executed");
  }

  @Test
  void doesNothingOnceAnyAdministratorExists() {
    when(accounts.existsOfKind(AccountKind.ADMIN)).thenReturn(true);

    AdminBootstrapResult result = service.bootstrap("first@example.com", "Str0ng-Bootstrap-pass");

    assertThat(result).isEqualTo(AdminBootstrapResult.SKIPPED_ADMIN_EXISTS);
    verify(accounts, never()).insert(any());
    verify(roles, never()).grant(any(), any(), any());
    verify(audit, never()).record(any());
  }

  @Test
  void rejectsAWeakBootstrapPasswordBeforeCreatingAnything() {
    when(accounts.existsOfKind(AccountKind.ADMIN)).thenReturn(false);

    assertThatThrownBy(() -> service.bootstrap("first@example.com", "password123"))
        .isInstanceOf(PasswordRejectedException.class);
    verify(accounts, never()).insert(any());
  }
}
