package com.zellastay.identity.application;

/** Proof of a correct admin password; only good for the second-factor step. */
public record MfaTicket(String value, boolean enrolled) {

  @Override
  public String toString() {
    return "MfaTicket[enrolled=" + enrolled + "]";
  }
}
