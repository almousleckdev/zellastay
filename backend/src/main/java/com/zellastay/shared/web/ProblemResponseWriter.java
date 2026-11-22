package com.zellastay.shared.web;

import com.zellastay.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the standard problem+json body from code that runs outside MVC (servlet filters, security
 * entry points), so those errors share the one contract produced by {@link ProblemFactory}.
 */
@Component
public class ProblemResponseWriter {

  private final ProblemFactory problems;
  private final JsonMapper json;

  ProblemResponseWriter(ProblemFactory problems, JsonMapper json) {
    this.problems = problems;
    this.json = json;
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      ErrorCode code,
      Map<String, String> headers)
      throws IOException {
    Locale previous = LocaleContextHolder.getLocale();
    LocaleContextHolder.setLocale(requestLocale(request));
    try {
      ProblemDetail problem = problems.create(code, List.of());
      response.setStatus(problem.getStatus());
      response.setContentType("application/problem+json");
      response.setCharacterEncoding("UTF-8");
      headers.forEach(response::setHeader);
      response.getWriter().write(json.writeValueAsString(problem));
    } finally {
      LocaleContextHolder.setLocale(previous);
    }
  }

  /** French unless the client asked for something else, matching the MVC locale resolver. */
  private static Locale requestLocale(HttpServletRequest request) {
    return request.getHeader("Accept-Language") == null ? Locale.FRENCH : request.getLocale();
  }
}
