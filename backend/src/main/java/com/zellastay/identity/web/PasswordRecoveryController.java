package com.zellastay.identity.web;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.application.RequestPasswordResetService;
import com.zellastay.identity.application.ResetPasswordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Forgot and reset password for each audience. */
@RestController
class PasswordRecoveryController {

  private final RequestPasswordResetService request;
  private final ResetPasswordService reset;

  PasswordRecoveryController(RequestPasswordResetService request, ResetPasswordService reset) {
    this.request = request;
    this.reset = reset;
  }

  @PostMapping("/api/v1/auth/password/forgot")
  ResponseEntity<Void> forgotCustomer(@Valid @RequestBody EmailRequest body) {
    return forgot(AccountKind.CUSTOMER, body);
  }

  @PostMapping("/api/v1/vendor/auth/password/forgot")
  ResponseEntity<Void> forgotVendorUser(@Valid @RequestBody EmailRequest body) {
    return forgot(AccountKind.VENDOR_USER, body);
  }

  @PostMapping("/api/v1/admin/auth/password/forgot")
  ResponseEntity<Void> forgotAdmin(@Valid @RequestBody EmailRequest body) {
    return forgot(AccountKind.ADMIN, body);
  }

  @PostMapping("/api/v1/auth/password/reset")
  ResponseEntity<Void> resetCustomer(@Valid @RequestBody ResetPasswordRequest body) {
    return complete(AccountKind.CUSTOMER, body);
  }

  @PostMapping("/api/v1/vendor/auth/password/reset")
  ResponseEntity<Void> resetVendorUser(@Valid @RequestBody ResetPasswordRequest body) {
    return complete(AccountKind.VENDOR_USER, body);
  }

  @PostMapping("/api/v1/admin/auth/password/reset")
  ResponseEntity<Void> resetAdmin(@Valid @RequestBody ResetPasswordRequest body) {
    return complete(AccountKind.ADMIN, body);
  }

  private ResponseEntity<Void> forgot(AccountKind kind, EmailRequest body) {
    request.request(kind, body.email());
    return ResponseEntity.accepted().build();
  }

  private ResponseEntity<Void> complete(AccountKind kind, ResetPasswordRequest body) {
    reset.reset(kind, body.token(), body.newPassword());
    return ResponseEntity.noContent().build();
  }
}
