package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request carrying the caller's current password. Secrets never appear in toString(). */
record PasswordRequest(@NotBlank @Size(max = 1024) String password) {

  @Override
  public String toString() {
    return "PasswordRequest[]";
  }
}
