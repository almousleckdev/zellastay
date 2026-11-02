package com.zellastay.identity.web;

import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.ChangePasswordService;
import com.zellastay.identity.application.SessionManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Sessions and password for the signed-in user, under each audience's own {@code /me} prefix. */
@RestController
class SelfServiceController {

  private static final String CUSTOMER = "/api/v1/me";
  private static final String VENDOR = "/api/v1/vendor/me";
  private static final String ADMIN = "/api/v1/admin/me";

  private final SessionManagementService sessions;
  private final ChangePasswordService passwords;

  SelfServiceController(SessionManagementService sessions, ChangePasswordService passwords) {
    this.sessions = sessions;
    this.passwords = passwords;
  }

  @GetMapping({CUSTOMER + "/sessions", VENDOR + "/sessions", ADMIN + "/sessions"})
  List<SessionView> list(@AuthenticationPrincipal CurrentPrincipal principal) {
    return sessions.list(principal).stream().map(SessionView::from).toList();
  }

  @DeleteMapping({CUSTOMER + "/sessions/{id}", VENDOR + "/sessions/{id}", ADMIN + "/sessions/{id}"})
  ResponseEntity<Void> revoke(
      @AuthenticationPrincipal CurrentPrincipal principal, @PathVariable UUID id) {
    sessions.revoke(principal, id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping({CUSTOMER + "/sessions", VENDOR + "/sessions", ADMIN + "/sessions"})
  ResponseEntity<Void> revokeAll(@AuthenticationPrincipal CurrentPrincipal principal) {
    sessions.revokeAll(principal);
    return ResponseEntity.noContent().build();
  }

  @PostMapping({CUSTOMER + "/password", VENDOR + "/password", ADMIN + "/password"})
  ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal CurrentPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest request) {
    passwords.change(principal, request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent().build();
  }
}
