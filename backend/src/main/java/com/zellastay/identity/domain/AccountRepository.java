package com.zellastay.identity.domain;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AccountKind;
import java.util.Optional;

public interface AccountRepository {

  /** Inserts a new account; a duplicate (kind, email) raises {@code DuplicateKeyException}. */
  void insert(Account account);

  /** Updates with optimistic locking on the loaded version. */
  void update(Account account);

  Optional<Account> findById(AccountId id);

  Optional<Account> findByEmail(AccountKind kind, Email email);

  boolean existsOfKind(AccountKind kind);
}
