package com.zellastay.identity.web;

import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.SessionGuard;
import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.identity.domain.AccessTokens.TokenClaims;
import com.zellastay.shared.security.TokenAudience;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from its bearer token for exactly one audience. A missing, invalid,
 * expired, wrong-audience or revoked token simply leaves the request anonymous, so the entry point
 * answers 401 without saying which of those it was.
 */
class BearerAuthenticationFilter extends OncePerRequestFilter {

  private static final String PREFIX = "Bearer ";
  private static final String MDC_ACCOUNT = "accountId";

  private final TokenAudience audience;
  private final AccessTokens tokens;
  private final SessionGuard sessions;
  private final PrincipalAuthorities authorities;
  private final Clock clock;

  BearerAuthenticationFilter(
      TokenAudience audience,
      AccessTokens tokens,
      SessionGuard sessions,
      PrincipalAuthorities authorities,
      Clock clock) {
    this.audience = audience;
    this.tokens = tokens;
    this.sessions = sessions;
    this.authorities = authorities;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      authenticate(request).ifPresent(this::establish);
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
      MDC.remove(MDC_ACCOUNT);
    }
  }

  private Optional<CurrentPrincipal> authenticate(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(PREFIX)) {
      return Optional.empty();
    }
    Optional<TokenClaims> claims =
        tokens.parse(header.substring(PREFIX.length()).strip(), audience, clock.instant());
    return claims
        .filter(c -> sessions.isUsable(c.sessionId()))
        .map(
            c -> new CurrentPrincipal(c.accountId(), c.audience(), c.sessionId(), c.mfaVerified()));
  }

  private void establish(CurrentPrincipal principal) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new AccountAuthentication(principal, authorities.forPrincipal(principal)));
    SecurityContextHolder.setContext(context);
    MDC.put(MDC_ACCOUNT, principal.accountId().value().toString());
  }
}
