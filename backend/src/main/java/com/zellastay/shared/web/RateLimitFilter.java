package com.zellastay.shared.web;

import com.zellastay.shared.CommonErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles API calls per client address before any authentication work happens. Authentication
 * endpoints get the strict policy. Answers 429 problem+json with Retry-After.
 */
class RateLimitFilter extends OncePerRequestFilter implements Ordered {

  static final String API_PREFIX = "/api/";
  private static final String[] AUTH_PREFIXES = {
    "/api/v1/auth/", "/api/v1/vendor/auth/", "/api/v1/admin/auth/"
  };

  private final RateLimitCounter counter;
  private final RateLimitProperties properties;
  private final ProblemResponseWriter writer;

  RateLimitFilter(
      RateLimitCounter counter, RateLimitProperties properties, ProblemResponseWriter writer) {
    this.counter = counter;
    this.properties = properties;
    this.writer = writer;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(API_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean auth = isAuthEndpoint(request.getRequestURI());
    RateLimitProperties.Policy policy = auth ? properties.auth() : properties.api();
    String key = (auth ? "auth:" : "api:") + request.getRemoteAddr();
    RateLimitCounter.Hit hit = counter.hit(key, policy.window());
    if (hit.count() > policy.requests()) {
      long retryAfter = Math.max(1, hit.remaining().toSeconds());
      writer.write(
          request,
          response,
          CommonErrorCode.RATE_LIMITED,
          Map.of("Retry-After", Long.toString(retryAfter)));
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean isAuthEndpoint(String path) {
    for (String prefix : AUTH_PREFIXES) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
