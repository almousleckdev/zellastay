package com.zellastay.shared.web;

import com.zellastay.shared.CommonErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Builds the validation problem: which fields failed and why, in the caller's language, and never
 * the rejected values.
 */
@Component
class ValidationProblems {

  private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

  private final ProblemFactory problems;

  ValidationProblems(ProblemFactory problems) {
    this.problems = problems;
  }

  ProblemDetail forBody(MethodArgumentNotValidException ex) {
    Locale locale = LocaleContextHolder.getLocale();
    List<Map<String, String>> errors = new ArrayList<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      String constraint = kebab(error.getCode() == null ? "invalid" : error.getCode());
      errors.add(entry(error.getField(), constraint, locale));
    }
    return problemWith(errors);
  }

  ProblemDetail forParameters(HandlerMethodValidationException ex) {
    Locale locale = LocaleContextHolder.getLocale();
    List<Map<String, String>> errors = new ArrayList<>();
    ex.getParameterValidationResults()
        .forEach(
            result -> {
              String name = result.getMethodParameter().getParameterName();
              errors.add(entry(name == null ? "parameter" : name, "invalid", locale));
            });
    return problemWith(errors);
  }

  private Map<String, String> entry(String field, String constraint, Locale locale) {
    return Map.of(
        "field",
        field,
        "code",
        "validation." + constraint,
        "message",
        problems.fieldMessage(constraint, locale));
  }

  private ProblemDetail problemWith(List<Map<String, String>> errors) {
    ProblemDetail problem = problems.create(CommonErrorCode.VALIDATION_FAILED, List.of());
    problem.setProperty("errors", errors);
    return problem;
  }

  private static String kebab(String constraint) {
    return CAMEL_BOUNDARY.matcher(constraint).replaceAll("$1-$2").toLowerCase(Locale.ROOT);
  }
}
