package com.zellastay.identity.domain;

public interface SecretGenerator {

  /** A six-digit code from a cryptographically secure source. */
  String numericCode();

  /** 256 random bits, URL-safe. */
  String token();
}
