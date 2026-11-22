package com.zellastay.identity.web;

import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.DeactivateAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Self-service account closure for customers and vendor users. */
@RestController
class DeactivationController {

  private final DeactivateAccountService deactivation;

  DeactivationController(DeactivateAccountService deactivation) {
    this.deactivation = deactivation;
  }

  @PostMapping({"/api/v1/me/deactivation", "/api/v1/vendor/me/deactivation"})
  ResponseEntity<Void> deactivate(
      @AuthenticationPrincipal CurrentPrincipal principal,
      @Valid @RequestBody PasswordRequest request) {
    deactivation.deactivate(principal, request.password());
    return ResponseEntity.noContent().build();
  }
}
