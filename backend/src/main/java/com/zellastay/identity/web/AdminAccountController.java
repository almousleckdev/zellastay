package com.zellastay.identity.web;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.AdminAccountService;
import com.zellastay.identity.web.AdminDtos.CreateAdminRequest;
import com.zellastay.identity.web.AdminDtos.CreatedResponse;
import com.zellastay.identity.web.AdminDtos.RoleRequest;
import com.zellastay.identity.web.AdminDtos.SuspendRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Administrator actions on accounts; authorization is enforced in {@link AdminAccountService}. */
@RestController
class AdminAccountController {

  private static final String BASE = "/api/v1/admin/accounts";

  private final AdminAccountService accounts;

  AdminAccountController(AdminAccountService accounts) {
    this.accounts = accounts;
  }

  @PostMapping(BASE)
  ResponseEntity<CreatedResponse> create(
      @AuthenticationPrincipal CurrentPrincipal actor,
      @Valid @RequestBody CreateAdminRequest request) {
    AccountId id =
        accounts.createAdmin(
            actor, request.email(), request.firstName(), request.lastName(), request.role());
    return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedResponse(id.value()));
  }

  @PostMapping(BASE + "/{id}/roles")
  ResponseEntity<Void> grant(
      @AuthenticationPrincipal CurrentPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody RoleRequest request) {
    accounts.grantRole(actor, new AccountId(id), request.role());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping(BASE + "/{id}/roles/{role}")
  ResponseEntity<Void> revoke(
      @AuthenticationPrincipal CurrentPrincipal actor,
      @PathVariable UUID id,
      @PathVariable AdminRole role) {
    accounts.revokeRole(actor, new AccountId(id), role);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(BASE + "/{id}/suspension")
  ResponseEntity<Void> suspend(
      @AuthenticationPrincipal CurrentPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody SuspendRequest request) {
    accounts.suspend(actor, new AccountId(id), request.reasonCode());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping(BASE + "/{id}/suspension")
  ResponseEntity<Void> reinstate(
      @AuthenticationPrincipal CurrentPrincipal actor, @PathVariable UUID id) {
    accounts.reinstate(actor, new AccountId(id));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping(BASE + "/{id}/sessions")
  ResponseEntity<Void> revokeSessions(
      @AuthenticationPrincipal CurrentPrincipal actor, @PathVariable UUID id) {
    accounts.revokeSessions(actor, new AccountId(id));
    return ResponseEntity.noContent().build();
  }
}
