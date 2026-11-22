package com.zellastay.identity.web;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.application.RegisterAccountService;
import com.zellastay.identity.application.RegisterCommand;
import com.zellastay.identity.application.ResendVerificationService;
import com.zellastay.identity.application.SupportedLocale;
import com.zellastay.identity.application.VerifyEmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Registration and email verification for customers and vendor users. */
@RestController
class RegistrationController {

  private final RegisterAccountService registration;
  private final VerifyEmailService verification;
  private final ResendVerificationService resending;

  RegistrationController(
      RegisterAccountService registration,
      VerifyEmailService verification,
      ResendVerificationService resending) {
    this.registration = registration;
    this.verification = verification;
    this.resending = resending;
  }

  @PostMapping("/api/v1/auth/register")
  ResponseEntity<Void> registerCustomer(@Valid @RequestBody RegisterRequest request) {
    return register(AccountKind.CUSTOMER, request);
  }

  @PostMapping("/api/v1/vendor/auth/register")
  ResponseEntity<Void> registerVendorUser(@Valid @RequestBody RegisterRequest request) {
    return register(AccountKind.VENDOR_USER, request);
  }

  @PostMapping("/api/v1/auth/verify")
  ResponseEntity<Void> verifyCustomer(@Valid @RequestBody VerifyRequest request) {
    verification.verify(AccountKind.CUSTOMER, request.email(), request.code());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/v1/vendor/auth/verify")
  ResponseEntity<Void> verifyVendorUser(@Valid @RequestBody VerifyRequest request) {
    verification.verify(AccountKind.VENDOR_USER, request.email(), request.code());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/v1/auth/verify/resend")
  ResponseEntity<Void> resendCustomer(@Valid @RequestBody EmailRequest request) {
    resending.resend(AccountKind.CUSTOMER, request.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/api/v1/vendor/auth/verify/resend")
  ResponseEntity<Void> resendVendorUser(@Valid @RequestBody EmailRequest request) {
    resending.resend(AccountKind.VENDOR_USER, request.email());
    return ResponseEntity.accepted().build();
  }

  private ResponseEntity<Void> register(AccountKind kind, RegisterRequest request) {
    registration.register(
        new RegisterCommand(
            kind,
            request.email(),
            request.password(),
            request.firstName(),
            request.lastName(),
            SupportedLocale.normalize(request.locale())));
    return ResponseEntity.accepted().build();
  }
}
