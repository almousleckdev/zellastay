package com.zellastay.shared.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "zellastay.rate-limit")
public record RateLimitProperties(@NotNull @Valid Policy auth, @NotNull @Valid Policy api) {

  public record Policy(@Min(1) int requests, @NotNull Duration window) {}
}
