package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;

public record RegisterCommand(
    AccountKind kind,
    String email,
    String password,
    String firstName,
    String lastName,
    String locale) {

  @Override
  public String toString() {
    return "RegisterCommand[kind=" + kind + "]";
  }
}
