package com.zellastay.shared.web;

import com.zellastay.shared.CommonErrorCode;
import com.zellastay.shared.ErrorCategory;
import com.zellastay.shared.ErrorCode;
import com.zellastay.shared.ZellaStayException;
import java.sql.SQLException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private final ProblemFactory problems;
  private final ValidationProblems validation;

  ApiExceptionHandler(ProblemFactory problems, ValidationProblems validation) {
    this.problems = problems;
    this.validation = validation;
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

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
    LOG.info("Request rejected code={} category=FORBIDDEN", CommonErrorCode.AUTH_FORBIDDEN.code());
    return respond(problems.create(CommonErrorCode.AUTH_FORBIDDEN, List.of()));
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<Object> handleAuthentication(AuthenticationException ex) {
    LOG.info(
        "Request rejected code={} category=UNAUTHENTICATED",
        CommonErrorCode.AUTH_UNAUTHENTICATED.code());
    return respond(problems.create(CommonErrorCode.AUTH_UNAUTHENTICATED, List.of()));
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
    return respond(validation.forBody(ex));
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return respond(validation.forParameters(ex));
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    CommonErrorCode code = StatusErrorCodes.codeFor(statusCode);
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

  private static ResponseEntity<Object> respond(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus()).contentType(problemJson()).body(problem);
  }

  private static MediaType problemJson() {
    return MediaType.APPLICATION_PROBLEM_JSON;
  }

  private static boolean isServerSide(ErrorCategory category) {
    return category == ErrorCategory.INTERNAL || category == ErrorCategory.UNAVAILABLE;
  }

  private static String describe(DataAccessException ex) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
    String sqlState = root instanceof SQLException sql ? sql.getSQLState() : "n/a";
    return "type=" + root.getClass().getSimpleName() + " sqlState=" + sqlState;
  }
}
