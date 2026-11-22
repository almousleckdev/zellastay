package com.zellastay.identity.web;

import com.zellastay.identity.api.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request to create an administrator. */
record CreateAdminRequest(
    @NotBlank @Size(max = 254) String email,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotNull AdminRole role) {}
