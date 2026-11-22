package com.zellastay.identity.web;

import com.zellastay.identity.application.AdminLoginService;
import com.zellastay.identity.application.AdminMfaEnrollmentService;
import com.zellastay.identity.application.AdminMfaVerificationService;
import com.zellastay.identity.application.MfaEnrollmentView;
import com.zellastay.identity.application.MfaTicket;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Two-step administrator sign-in: password, then authenticator or recovery code. */
@RestController
class AdminAuthController {

  private final AdminLoginService login;
  private final AdminMfaEnrollmentService enrollment;
  private final AdminMfaVerificationService verification;

  AdminAuthController(
      AdminLoginService login,
      AdminMfaEnrollmentService enrollment,
      AdminMfaVerificationService verification) {
    this.login = login;
    this.enrollment = enrollment;
    this.verification = verification;
  }

  @PostMapping("/api/v1/admin/auth/login")
  MfaTicketResponse login(@Valid @RequestBody LoginRequest request) {
    MfaTicket ticket = login.login(request.email(), request.password());
    return new MfaTicketResponse(ticket.value(), ticket.enrolled());
  }

  @PostMapping("/api/v1/admin/auth/mfa/enroll")
  EnrollmentResponse enroll(@Valid @RequestBody MfaTicketRequest request) {
    MfaEnrollmentView view = enrollment.enroll(request.mfaTicket());
    return new EnrollmentResponse(view.secret(), view.otpauthUri());
  }

  @PostMapping("/api/v1/admin/auth/mfa/verify")
  AdminTokenResponse verify(@Valid @RequestBody MfaVerifyRequest request) {
    return AdminTokenResponse.from(
        verification.verify(request.mfaTicket(), request.code(), request.recoveryCode()));
  }
}
