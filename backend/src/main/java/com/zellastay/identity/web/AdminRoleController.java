package com.zellastay.identity.web;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.AdminRoleService;
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

/** Grants and revokes admin roles; authorization is enforced in {@link AdminRoleService}. */
@RestController
@RequestMapping("/api/v1/admin/accounts/{id}/roles")
class AdminRoleController {

  private final AdminRoleService roles;

  AdminRoleController(AdminRoleService roles) {
    this.roles = roles;
  }

  @PostMapping
  ResponseEntity<Void> grant(
      @AuthenticationPrincipal CurrentPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody RoleRequest request) {
    roles.grantRole(actor, new AccountId(id), request.role());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{role}")
  ResponseEntity<Void> revoke(
      @AuthenticationPrincipal CurrentPrincipal actor,
      @PathVariable UUID id,
      @PathVariable AdminRole role) {
    roles.revokeRole(actor, new AccountId(id), role);
    return ResponseEntity.noContent().build();
  }
}
