package com.zellastay.identity.infrastructure;

import static com.zellastay.support.AccountFixtures.uniqueEmail;
import static com.zellastay.support.RepositoryTestData.NOW;
import static com.zellastay.support.RepositoryTestData.newAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.Email;
import com.zellastay.identity.domain.Profile;
import com.zellastay.support.IntegrationTest;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/** Persistence of accounts: round trip, uniqueness and optimistic locking. */
@IntegrationTest
class AccountRepositoryIT {

  @Autowired AccountRepository accounts;
  @Autowired TransactionTemplate transaction;

  @Test
  void anAccountRoundTripsWithItsProfile() {
    Account account = newAccount(AccountKind.CUSTOMER, uniqueEmail());
    transaction.executeWithoutResult(s -> accounts.insert(account));

    Account loaded = accounts.findById(account.id()).orElseThrow();

    assertThat(loaded.snapshot()).isEqualTo(account.snapshot());
    assertThat(loaded.snapshot().profile()).isEqualTo(new Profile("Jane", "Doe"));
  }

  @Test
  void anEmailIsUniquePerKindButMayExistInDifferentKinds() {
    String email = uniqueEmail();
    transaction.executeWithoutResult(s -> accounts.insert(newAccount(AccountKind.CUSTOMER, email)));
    transaction.executeWithoutResult(
        s -> accounts.insert(newAccount(AccountKind.VENDOR_USER, email)));

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    s -> accounts.insert(newAccount(AccountKind.CUSTOMER, email))))
        .isInstanceOf(DuplicateKeyException.class);
    assertThat(accounts.findByEmail(AccountKind.CUSTOMER, Email.of(email))).isPresent();
    assertThat(accounts.findByEmail(AccountKind.ADMIN, Email.of(email))).isEmpty();
  }

  @Test
  void concurrentRegistrationsOfTheSameEmailCreateExactlyOneAccount() throws Exception {
    String email = uniqueEmail();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Boolean>> attempts =
          java.util.stream.IntStream.range(0, 8)
              .<Callable<Boolean>>mapToObj(
                  i ->
                      () -> {
                        try {
                          transaction.executeWithoutResult(
                              s -> accounts.insert(newAccount(AccountKind.CUSTOMER, email)));
                          return true;
                        } catch (DuplicateKeyException e) {
                          return false;
                        }
                      })
              .toList();
      long winners = 0;
      for (Future<Boolean> result : pool.invokeAll(attempts)) {
        winners += result.get() ? 1 : 0;
      }
      assertThat(winners).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void updatesAreOptimisticallyLocked() {
    Account account = newAccount(AccountKind.CUSTOMER, uniqueEmail());
    transaction.executeWithoutResult(s -> accounts.insert(account));
    Account first = accounts.findById(account.id()).orElseThrow();
    Account second = accounts.findById(account.id()).orElseThrow();
    first.verifyEmail(NOW);
    second.suspend();

    transaction.executeWithoutResult(s -> accounts.update(first));

    assertThatThrownBy(() -> transaction.executeWithoutResult(s -> accounts.update(second)))
        .isInstanceOf(OptimisticLockingFailureException.class);
    Account stored = accounts.findById(account.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(stored.snapshot().version()).isEqualTo(1);
  }
}
