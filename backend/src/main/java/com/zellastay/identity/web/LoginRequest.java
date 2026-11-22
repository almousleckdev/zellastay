package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(
    @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 1024) String password) {

  @Override
  public String toString() {
    return "LoginRequest[]";
  }
}
