package com.zellastay.identity.web;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.application.LoginService;
import com.zellastay.identity.application.LogoutService;
import com.zellastay.identity.application.RefreshSessionService;
import com.zellastay.shared.security.TokenAudience;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Login, refresh and logout for customers and vendor users (admin has its own controller). */
@RestController
class AuthController {

  private final LoginService login;
  private final RefreshSessionService refresh;
  private final LogoutService logout;

  AuthController(LoginService login, RefreshSessionService refresh, LogoutService logout) {
    this.login = login;
    this.refresh = refresh;
    this.logout = logout;
  }

  @PostMapping("/api/v1/auth/login")
  TokenResponse loginCustomer(@Valid @RequestBody LoginRequest request) {
    return TokenResponse.from(
        login.login(AccountKind.CUSTOMER, request.email(), request.password()));
  }

  @PostMapping("/api/v1/vendor/auth/login")
  TokenResponse loginVendorUser(@Valid @RequestBody LoginRequest request) {
    return TokenResponse.from(
        login.login(AccountKind.VENDOR_USER, request.email(), request.password()));
  }

  @PostMapping("/api/v1/auth/refresh")
  TokenResponse refreshCustomer(@Valid @RequestBody RefreshRequest request) {
    return TokenResponse.from(refresh.refresh(TokenAudience.CUSTOMER, request.refreshToken()));
  }

  @PostMapping("/api/v1/vendor/auth/refresh")
  TokenResponse refreshVendorUser(@Valid @RequestBody RefreshRequest request) {
    return TokenResponse.from(refresh.refresh(TokenAudience.VENDOR, request.refreshToken()));
  }

  @PostMapping("/api/v1/admin/auth/refresh")
  TokenResponse refreshAdmin(@Valid @RequestBody RefreshRequest request) {
    return TokenResponse.from(refresh.refresh(TokenAudience.ADMIN, request.refreshToken()));
  }

  @PostMapping({"/api/v1/auth/logout", "/api/v1/vendor/auth/logout", "/api/v1/admin/auth/logout"})
  ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
    logout.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }
}
