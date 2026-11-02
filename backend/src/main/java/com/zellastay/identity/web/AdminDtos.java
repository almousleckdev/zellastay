package com.zellastay.identity.web;

import com.zellastay.identity.api.AdminRole;
import com.zellastay.identity.application.AdminSession;
import com.zellastay.identity.application.TokenPair;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the admin endpoints. Secrets never appear in toString(). */
final class AdminDtos {

  private AdminDtos() {}

  record MfaTicketResponse(String mfaTicket, boolean mfaEnrolled) {

    @Override
    public String toString() {
      return "MfaTicketResponse[]";
    }
  }

  record MfaTicketRequest(@NotBlank @Size(max = 128) String mfaTicket) {}

  record EnrollmentResponse(String secret, String otpauthUri) {

    @Override
    public String toString() {
      return "EnrollmentResponse[]";
    }
  }

  record MfaVerifyRequest(
      @NotBlank @Size(max = 128) String mfaTicket,
      @Pattern(regexp = "\\d{6}") String code,
      @Size(max = 32) String recoveryCode) {

    @AssertTrue(message = "exactly one of code and recoveryCode is required")
    boolean isExactlyOneFactor() {
      return (code != null) != (recoveryCode != null && !recoveryCode.isBlank());
    }

    @Override
    public String toString() {
      return "MfaVerifyRequest[]";
    }
  }

  record AdminTokenResponse(
      String accessToken,
      String tokenType,
      long expiresIn,
      String refreshToken,
      List<String> recoveryCodes) {

    static AdminTokenResponse from(AdminSession session) {
      TokenPair pair = session.tokens();
      return new AdminTokenResponse(
          pair.accessToken(),
          "Bearer",
          pair.expiresInSeconds(),
          pair.refreshToken(),
          session.recoveryCodes());
    }

    @Override
    public String toString() {
      return "AdminTokenResponse[]";
    }
  }

  record CreateAdminRequest(
      @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @NotNull AdminRole role) {}

  record CreatedResponse(UUID id) {}

  record RoleRequest(@NotNull AdminRole role) {}

  record SuspendRequest(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String reasonCode) {}

  record PasswordRequest(@NotBlank @Size(max = 1024) String password) {

    @Override
    public String toString() {
      return "PasswordRequest[]";
    }
  }
}
