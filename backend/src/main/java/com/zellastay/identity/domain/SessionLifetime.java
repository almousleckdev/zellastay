package com.zellastay.identity.domain;

import java.time.Duration;

public record SessionLifetime(Duration absolute, Duration idle) {}
