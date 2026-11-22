package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record EmailRequest(@NotBlank @Size(max = 254) String email) {

  @Override
  public String toString() {
    return "EmailRequest[]";
  }
}
