package com.zellastay.identity.domain;

/**
 * Hashing for one-time secrets. Short codes are keyed (peppered); long random tokens are digested.
 */
public interface SecretHasher {

  /** HMAC with the server pepper; for low-entropy secrets such as 6-digit codes. */
  String keyed(String secret);

  /** Plain SHA-256; for high-entropy random tokens. */
  String digest(String secret);

  /** Constant-time equality of two hash strings. */
  boolean sameSecret(String expected, String actual);
}
