package com.zellastay.audit.api;

public enum ActorType {
  CUSTOMER,
  VENDOR_USER,
  ADMIN,
  SYSTEM,
  ANONYMOUS;

  boolean isAccount() {
    return this == CUSTOMER || this == VENDOR_USER || this == ADMIN;
  }
}
