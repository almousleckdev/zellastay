package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record VerifyRequest(
    @NotBlank @Size(max = 254) String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {

  @Override
  public String toString() {
    return "VerifyRequest[]";
  }
}
