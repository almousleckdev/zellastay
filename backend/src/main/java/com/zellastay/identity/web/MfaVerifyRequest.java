package com.zellastay.identity.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request completing admin sign-in with a code or a recovery code. Secrets never appear in
 * toString().
 */
record MfaVerifyRequest(
    @NotBlank @Size(max = 128) String mfaTicket,
    @Pattern(regexp = "\\d{6}") String code,
    @Size(max = 32) String recoveryCode) {

  @AssertTrue(message = "exactly one of code and recoveryCode is required")
  boolean isExactlyOneFactor() {
    return (code != null) != (recoveryCode != null && !recoveryCode.isBlank());
  }

  @Override
  public String toString() {
    return "MfaVerifyRequest[]";
  }
}
