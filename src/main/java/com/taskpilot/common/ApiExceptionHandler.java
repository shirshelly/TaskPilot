package com.taskpilot.common;

import com.taskpilot.task.TaskNotFoundException;
import com.taskpilot.task.TaskStateException;
import com.taskpilot.task.TaskValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 Problem Details with a stable {@code code} property (§33), keeping
 * HTTP concerns out of the domain/application layer.
 *
 * Domain exceptions carry messages we author, so their text is safe to return as {@code detail}.
 * Spring MVC's own exceptions (binding, body parsing, method validation, unknown routes) are
 * handled by {@link ResponseEntityExceptionHandler} with Spring's generic details, so parser or
 * framework internals never reach the client; only the code is added. Anything else is logged
 * and returned as a generic 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    ProblemDetail handleTaskNotFound(TaskNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ApiErrorCode.TASK_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TaskStateException.class)
    ProblemDetail handleInvalidStateTransition(TaskStateException ex) {
        return problem(HttpStatus.CONFLICT, ApiErrorCode.INVALID_STATE_TRANSITION, ex.getMessage());
    }

    @ExceptionHandler(TaskValidationException.class)
    ProblemDetail handleDomainValidation(TaskValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, ex.getMessage());
    }

    // Stale @Version write detected at flush/commit (§42). Spring translates Hibernate's
    // StaleObjectStateException into ObjectOptimisticLockingFailureException; mapping its portable
    // parent keeps this independent of the JPA provider. Not retried: the client re-reads and decides.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleStaleWrite(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, ApiErrorCode.VERSION_CONFLICT,
                "Task was modified concurrently; reload and retry");
    }

    // Last resort. More specific handlers (above and in the superclass) always win, because Spring
    // picks the closest matching exception type. The message may contain internals: log, don't return.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setProperty("code", codeForFrameworkStatus(statusCode).name());
        }
        return response;
    }

    private static ApiErrorCode codeForFrameworkStatus(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return ApiErrorCode.VALIDATION_FAILED;
        }
        return statusCode.is4xxClientError() ? ApiErrorCode.REQUEST_NOT_SUPPORTED : ApiErrorCode.INTERNAL_ERROR;
    }

    private static ProblemDetail problem(HttpStatus status, ApiErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
