package com.zellastay.identity.web;

import com.zellastay.identity.api.AdminRole;
import jakarta.validation.constraints.NotNull;

/** Request naming an admin role. */
record RoleRequest(@NotNull AdminRole role) {}
