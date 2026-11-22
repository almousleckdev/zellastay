package com.zellastay.identity.web;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.AccountSessionRevocationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Signs another account out everywhere; authorization is enforced in the service. */
@RestController
@RequestMapping("/api/v1/admin/accounts/{id}/sessions")
class AccountSessionController {

  private final AccountSessionRevocationService revocation;

  AccountSessionController(AccountSessionRevocationService revocation) {
    this.revocation = revocation;
  }

  @DeleteMapping
  ResponseEntity<Void> revokeSessions(
      @AuthenticationPrincipal CurrentPrincipal actor, @PathVariable UUID id) {
    revocation.revokeSessions(actor, new AccountId(id));
    return ResponseEntity.noContent().build();
  }
}
