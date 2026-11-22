package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ChangePasswordRequest(
    @NotBlank @Size(max = 1024) String currentPassword,
    @NotBlank @Size(max = 1024) String newPassword) {

  @Override
  public String toString() {
    return "ChangePasswordRequest[]";
  }
}
