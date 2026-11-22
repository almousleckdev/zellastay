package com.zellastay.identity.web;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.AccountSuspensionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Suspends and reinstates accounts; authorization is enforced in the service. */
@RestController
@RequestMapping("/api/v1/admin/accounts/{id}/suspension")
class AccountSuspensionController {

  private final AccountSuspensionService suspensions;

  AccountSuspensionController(AccountSuspensionService suspensions) {
    this.suspensions = suspensions;
  }

  @PostMapping
  ResponseEntity<Void> suspend(
      @AuthenticationPrincipal CurrentPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody SuspendRequest request) {
    suspensions.suspend(actor, new AccountId(id), request.reasonCode());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
  ResponseEntity<Void> reinstate(
      @AuthenticationPrincipal CurrentPrincipal actor, @PathVariable UUID id) {
    suspensions.reinstate(actor, new AccountId(id));
    return ResponseEntity.noContent().build();
  }
}
