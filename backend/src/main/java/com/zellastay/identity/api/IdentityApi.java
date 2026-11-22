package com.zellastay.identity.api;

import java.util.Optional;

/** Read access to accounts for other modules (organization, notification). */
public interface IdentityApi {

  Optional<AccountSummary> findAccount(AccountId id);

  /**
   * The active vendor user whose verified email equals {@code email}; used to accept staff
   * invitations. Empty for unknown, unverified, inactive or non-vendor accounts.
   */
  Optional<AccountSummary> findVendorUserByVerifiedEmail(String email);
}
