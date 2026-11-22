package com.zellastay.shared.web;

import com.zellastay.shared.ErrorCode;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemFactory {

  private final MessageSource messages;

  ProblemFactory(MessageSource messages) {
    this.messages = messages;
  }

  ProblemDetail create(ErrorCode code, List<String> arguments) {
    return create(code, ErrorStatuses.statusOf(code.category()), arguments);
  }

  ProblemDetail create(ErrorCode code, HttpStatusCode status, List<String> arguments) {
    Locale locale = LocaleContextHolder.getLocale();
    Object[] args = arguments.toArray();
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create("urn:zellastay:error:" + code.code()));
    problem.setTitle(text(code, "title", args, locale));
    problem.setDetail(text(code, "detail", args, locale));
    problem.setProperty("code", code.code());
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    if (requestId != null) {
      problem.setProperty("requestId", requestId);
    }
    return problem;
  }

  String fieldMessage(String constraint, Locale locale) {
    String generic = messages.getMessage("error.validation.invalid", null, constraint, locale);
    return messages.getMessage("error.validation." + constraint, null, generic, locale);
  }

  private String text(ErrorCode code, String part, Object[] args, Locale locale) {
    String categoryKey = "error.category." + code.category().name().toLowerCase(Locale.ROOT);
    String categoryText = messages.getMessage(categoryKey + "." + part, args, code.code(), locale);
    return messages.getMessage("error." + code.code() + "." + part, args, categoryText, locale);
  }
}
