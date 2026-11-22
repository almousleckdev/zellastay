package com.zellastay.identity.web;

import com.zellastay.shared.CommonErrorCode;
import com.zellastay.shared.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 401 and 403 share the standard problem+json contract with every other error. */
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ProblemResponseWriter writer;

  ProblemSecurityHandlers(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    writer.write(
        request,
        response,
        CommonErrorCode.AUTH_UNAUTHENTICATED,
        Map.of("WWW-Authenticate", "Bearer"));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    writer.write(request, response, CommonErrorCode.AUTH_FORBIDDEN, Map.of());
  }
}
