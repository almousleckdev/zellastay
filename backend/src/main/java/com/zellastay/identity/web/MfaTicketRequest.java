package com.zellastay.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to start enrolment with a second-factor ticket. */
record MfaTicketRequest(@NotBlank @Size(max = 128) String mfaTicket) {}
