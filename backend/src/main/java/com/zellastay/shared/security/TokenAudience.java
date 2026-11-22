package com.zellastay.shared.security;

/** Audience a token is issued for; a token is accepted only by the filter chain of its audience. */
public enum TokenAudience {
  CUSTOMER("zs-customer"),
  VENDOR("zs-vendor"),
  ADMIN("zs-admin");

  private final String value;

  TokenAudience(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
