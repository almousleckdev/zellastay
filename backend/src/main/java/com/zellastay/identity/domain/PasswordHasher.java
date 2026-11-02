package com.zellastay.identity.domain;

public interface PasswordHasher {

  String hash(String password);

  boolean matches(String password, String hash);

  /** True when the stored hash was made with weaker parameters than the current ones. */
  boolean needsRehash(String hash);

  /** Spends the same time as a real verification, so unknown accounts are not distinguishable. */
  void burn(String password);
}
