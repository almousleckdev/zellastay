package com.zellastay.identity.application;

import java.time.Duration;

public record MfaSettings(Duration ticketTtl, int maxAttempts, String issuerLabel) {}
