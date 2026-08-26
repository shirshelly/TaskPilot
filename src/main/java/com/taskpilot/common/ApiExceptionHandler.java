package com.taskpilot.common;

import com.taskpilot.task.TaskNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * Maps application/domain exceptions to HTTP responses, keeping HTTP concerns out of the
 * domain/application layer itself. Delegates to the servlet container's standard error
 * dispatch (same as Spring's default handling for uncaught exceptions), so the response
 * body shape stays exactly what it already is elsewhere in the API -- no custom error
 * schema introduced.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    void handleTaskNotFound(TaskNotFoundException ex, HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value(), ex.getMessage());
    }
}
