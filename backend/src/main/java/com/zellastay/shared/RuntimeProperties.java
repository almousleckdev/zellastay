package com.zellastay.shared;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "zellastay")
public record RuntimeProperties(@NotNull RuntimeEnvironment environment) {}
