package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RefreshRequest(@NotBlank @Size(max = 256) String refreshToken) {

  @Override
  public String toString() {
    return "RefreshRequest[]";
  }
}
