package com.zellastay.identity.domain;

import java.time.Duration;

public record ChallengeSettings(
    Duration codeTtl,
    int maxAttempts,
    Duration resendCooldown,
    int maxSendsPerHour,
    Duration resetTokenTtl) {}
