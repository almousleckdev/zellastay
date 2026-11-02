package com.zellastay.identity.web;

import com.zellastay.identity.application.AdminLoginService;
import com.zellastay.identity.application.AdminMfaService;
import com.zellastay.identity.application.MfaEnrollmentView;
import com.zellastay.identity.application.MfaTicket;
import com.zellastay.identity.web.AdminDtos.AdminTokenResponse;
import com.zellastay.identity.web.AdminDtos.EnrollmentResponse;
import com.zellastay.identity.web.AdminDtos.MfaTicketRequest;
import com.zellastay.identity.web.AdminDtos.MfaTicketResponse;
import com.zellastay.identity.web.AdminDtos.MfaVerifyRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Two-step administrator sign-in: password, then authenticator or recovery code. */
@RestController
class AdminAuthController {

  private final AdminLoginService login;
  private final AdminMfaService mfa;

  AdminAuthController(AdminLoginService login, AdminMfaService mfa) {
    this.login = login;
    this.mfa = mfa;
  }

  @PostMapping("/api/v1/admin/auth/login")
  MfaTicketResponse login(@Valid @RequestBody LoginRequest request) {
    MfaTicket ticket = login.login(request.email(), request.password());
    return new MfaTicketResponse(ticket.value(), ticket.enrolled());
  }

  @PostMapping("/api/v1/admin/auth/mfa/enroll")
  EnrollmentResponse enroll(@Valid @RequestBody MfaTicketRequest request) {
    MfaEnrollmentView view = mfa.enroll(request.mfaTicket());
    return new EnrollmentResponse(view.secret(), view.otpauthUri());
  }

  @PostMapping("/api/v1/admin/auth/mfa/verify")
  AdminTokenResponse verify(@Valid @RequestBody MfaVerifyRequest request) {
    return AdminTokenResponse.from(
        mfa.verify(request.mfaTicket(), request.code(), request.recoveryCode()));
  }
}
