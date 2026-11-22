package com.zellastay.identity.web;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.CreateAdminAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates administrators; authorization is enforced in {@link CreateAdminAccountService}. */
@RestController
@RequestMapping("/api/v1/admin/accounts")
class AdminAccountController {

  private final CreateAdminAccountService creation;

  AdminAccountController(CreateAdminAccountService creation) {
    this.creation = creation;
  }

  @PostMapping
  ResponseEntity<CreatedResponse> create(
      @AuthenticationPrincipal CurrentPrincipal actor,
      @Valid @RequestBody CreateAdminRequest request) {
    AccountId id =
        creation.createAdmin(
            actor, request.email(), request.firstName(), request.lastName(), request.role());
    return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedResponse(id.value()));
  }
}
