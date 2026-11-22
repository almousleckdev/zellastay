package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request to suspend an account for a coded reason. */
record SuspendRequest(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String reasonCode) {}
