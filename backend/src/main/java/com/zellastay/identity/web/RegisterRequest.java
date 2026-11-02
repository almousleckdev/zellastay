package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterRequest(
    @NotBlank @Size(max = 254) String email,
    @NotBlank @Size(max = 1024) String password,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Size(max = 16) String locale) {

  @Override
  public String toString() {
    return "RegisterRequest[]";
  }
}
