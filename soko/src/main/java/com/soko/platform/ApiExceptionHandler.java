package com.soko.platform;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Errors.NotFound.class)
    public ProblemDetail onNotFound(Errors.NotFound ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail onUnknownPath(NoResourceFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "No endpoint at this path", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail onMethod(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "method-not-allowed",
                "That method is not supported on this path",
                request);
    }

    @ExceptionHandler(Errors.BadRequest.class)
    public ProblemDetail onBadRequest(Errors.BadRequest ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", ex.getMessage(), request);
    }

    @ExceptionHandler(Errors.Unroutable.class)
    public ProblemDetail onUnroutable(Errors.Unroutable ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "unroutable", ex.getMessage(), request);
    }

    @ExceptionHandler(Errors.Unauthorized.class)
    public ProblemDetail onUnauthorized(Errors.Unauthorized ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onInvalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + " " + e.getDefaultMessage())
                        .findFirst()
                        .orElse("the request did not validate");
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", detail, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled error on {}", request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "An unexpected error occurred",
                request);
    }

    private ProblemDetail problem(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code);
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }
}
