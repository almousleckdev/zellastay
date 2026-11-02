package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.api.AccountStatus;
import com.zellastay.identity.api.AccountSummary;
import com.zellastay.identity.api.EmailInvalidException;
import com.zellastay.identity.api.IdentityApi;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.AccountSnapshot;
import com.zellastay.identity.domain.Email;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityApiService implements IdentityApi {

  private final AccountRepository accounts;

  IdentityApiService(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AccountSummary> findAccount(AccountId id) {
    return accounts.findById(id).map(IdentityApiService::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AccountSummary> findVendorUserByVerifiedEmail(String email) {
    try {
      return accounts
          .findByEmail(AccountKind.VENDOR_USER, Email.of(email))
          .filter(account -> account.status() == AccountStatus.ACTIVE)
          .filter(account -> account.snapshot().emailVerifiedAt() != null)
          .map(IdentityApiService::summary);
    } catch (EmailInvalidException invalid) {
      return Optional.empty();
    }
  }

  private static AccountSummary summary(Account account) {
    AccountSnapshot s = account.snapshot();
    return new AccountSummary(
        s.id(), s.kind(), s.status(), s.emailVerifiedAt() != null, s.email().masked(), s.locale());
  }
}
