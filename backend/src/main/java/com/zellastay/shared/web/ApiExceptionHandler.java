package com.zellastay.shared.web;

import com.zellastay.shared.CommonErrorCode;
import com.zellastay.shared.ErrorCategory;
import com.zellastay.shared.ErrorCode;
import com.zellastay.shared.ZellaStayException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

  private final ProblemFactory problems;

  ApiExceptionHandler(ProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(ZellaStayException.class)
  ResponseEntity<Object> handleDomain(ZellaStayException ex) {
    ErrorCode code = ex.errorCode();
    if (isServerSide(code.category())) {
      LOG.error("Request failed code={} category={}", code.code(), code.category(), ex);
    } else {
      LOG.info("Request rejected code={} category={}", code.code(), code.category());
    }
    return respond(problems.create(code, ex.messageArguments()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex) {
    LOG.warn("Data integrity violation {}", describe(ex));
    return respond(problems.create(CommonErrorCode.DATA_CONFLICT, List.of()));
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex) {
    LOG.info("Concurrent modification rejected {}", describe(ex));
    return respond(problems.create(CommonErrorCode.DATA_CONCURRENT_MODIFICATION, List.of()));
  }

  @ExceptionHandler(TransientDataAccessException.class)
  ResponseEntity<Object> handleTransient(TransientDataAccessException ex) {
    LOG.warn("Transient data access failure {}", describe(ex));
    return respond(problems.create(CommonErrorCode.SERVICE_BUSY, List.of()));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Object> handleDataAccess(DataAccessException ex) {
    LOG.error("Data access failure", ex);
    return respond(problems.create(CommonErrorCode.INTERNAL_ERROR, List.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex) {
    LOG.error("Unhandled exception", ex);
    return respond(problems.create(CommonErrorCode.INTERNAL_ERROR, List.of()));
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors = new ArrayList<>();
    Locale locale = LocaleContextHolder.getLocale();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      String constraint = kebab(error.getCode() == null ? "invalid" : error.getCode());
      errors.add(
          Map.of(
              "field", error.getField(),
              "code", "validation." + constraint,
              "message", problems.fieldMessage(constraint, locale)));
    }
    return validationResponse(errors);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors = new ArrayList<>();
    Locale locale = LocaleContextHolder.getLocale();
    ex.getParameterValidationResults()
        .forEach(
            result -> {
              String name =
                  result.getMethodParameter().getParameterName() == null
                      ? "parameter"
                      : result.getMethodParameter().getParameterName();
              errors.add(
                  Map.of(
                      "field",
                      name,
                      "code",
                      "validation.invalid",
                      "message",
                      problems.fieldMessage("invalid", locale)));
            });
    return validationResponse(errors);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    CommonErrorCode code = codeFor(statusCode);
    if (statusCode.is5xxServerError()) {
      LOG.error("Request failed status={}", statusCode.value(), ex);
    } else {
      LOG.info(
          "Request rejected status={} cause={}", statusCode.value(), ex.getClass().getSimpleName());
    }
    ProblemDetail problem = problems.create(code, statusCode, List.of());
    return ResponseEntity.status(statusCode)
        .headers(headers)
        .contentType(problemJson())
        .body(problem);
  }

  static CommonErrorCode codeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
      case 405 -> CommonErrorCode.REQUEST_METHOD_NOT_ALLOWED;
      case 406 -> CommonErrorCode.REQUEST_NOT_ACCEPTABLE;
      case 412 -> CommonErrorCode.PRECONDITION_FAILED;
      case 413 -> CommonErrorCode.REQUEST_TOO_LARGE;
      case 415 -> CommonErrorCode.REQUEST_UNSUPPORTED_MEDIA_TYPE;
      case 429 -> CommonErrorCode.RATE_LIMITED;
      case 503 -> CommonErrorCode.SERVICE_BUSY;
      default ->
          status.is5xxServerError()
              ? CommonErrorCode.INTERNAL_ERROR
              : CommonErrorCode.REQUEST_MALFORMED;
    };
  }

  private ResponseEntity<Object> validationResponse(List<Map<String, String>> errors) {
    ProblemDetail problem = problems.create(CommonErrorCode.VALIDATION_FAILED, List.of());
    problem.setProperty("errors", errors);
    return respond(problem);
  }

  private static ResponseEntity<Object> respond(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus()).contentType(problemJson()).body(problem);
  }

  private static MediaType problemJson() {
    return MediaType.APPLICATION_PROBLEM_JSON;
  }

  private static boolean isServerSide(ErrorCategory category) {
    return category == ErrorCategory.INTERNAL || category == ErrorCategory.UNAVAILABLE;
  }

  private static String kebab(String constraint) {
    return CAMEL_BOUNDARY.matcher(constraint).replaceAll("$1-$2").toLowerCase(Locale.ROOT);
  }

  private static String describe(DataAccessException ex) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
    String sqlState = root instanceof SQLException sql ? sql.getSQLState() : "n/a";
    return "type=" + root.getClass().getSimpleName() + " sqlState=" + sqlState;
  }
}
