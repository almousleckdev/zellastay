package com.zellastay.identity.api;

import com.zellastay.shared.security.TokenAudience;
import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated caller, as other modules see it. It carries no organization or permission data:
 * tenant access is decided by the organization module from {@code (accountId, organizationId)}.
 */
public record CurrentPrincipal(
    AccountId accountId, TokenAudience audience, UUID sessionId, boolean mfaVerified) {

  public CurrentPrincipal {
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(audience, "audience");
    Objects.requireNonNull(sessionId, "sessionId");
  }

  public AccountKind kind() {
    return AccountKind.of(audience);
  }
}
