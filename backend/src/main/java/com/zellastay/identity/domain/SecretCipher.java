package com.zellastay.identity.domain;

/** Reversible encryption for secrets that must be read back (such as authenticator seeds). */
public interface SecretCipher {

  String encrypt(String plaintext);

  String decrypt(String stored);
}
