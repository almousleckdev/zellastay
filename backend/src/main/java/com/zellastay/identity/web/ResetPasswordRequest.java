package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ResetPasswordRequest(
    @NotBlank @Size(max = 128) String token, @NotBlank @Size(max = 1024) String newPassword) {

  @Override
  public String toString() {
    return "ResetPasswordRequest[]";
  }
}
