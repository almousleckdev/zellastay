package com.zellastay.identity.api;

import com.zellastay.shared.security.TokenAudience;

/** The audience an account belongs to; fixed for the life of the account. */
public enum AccountKind {
  CUSTOMER(TokenAudience.CUSTOMER),
  VENDOR_USER(TokenAudience.VENDOR),
  ADMIN(TokenAudience.ADMIN);

  private final TokenAudience audience;

  AccountKind(TokenAudience audience) {
    this.audience = audience;
  }

  public static AccountKind of(TokenAudience audience) {
    for (AccountKind kind : values()) {
      if (kind.audience == audience) {
        return kind;
      }
    }
    throw new IllegalArgumentException("no account kind for " + audience);
  }

  public TokenAudience audience() {
    return audience;
  }
}
