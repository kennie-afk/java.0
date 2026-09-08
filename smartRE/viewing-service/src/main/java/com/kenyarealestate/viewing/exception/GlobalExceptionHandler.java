package com.kenyarealestate.viewing.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * A request for a path this service does not serve.
     *
     * <p>Without this, Spring's NoResourceFoundException falls through to the catch-all
     * below and is reported as 500 "An unexpected error occurred." That is worse than
     * untidy: a client typing the wrong URL then shows up in dashboards as a server
     * fault, which inflates the error rate the deployment is judged on and wakes
     * somebody for a mistake no server made.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noSuchRoute(NoResourceFoundException e) {
        log.debug("No route for {}", e.getResourcePath());
        return build(HttpStatus.NOT_FOUND, "No such endpoint.");
    }
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(ForbiddenException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> springAccessDenied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(UnauthorizedException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> dataConflict(Exception e) {
        log.warn("Data integrity/concurrency conflict: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, "This request conflicts with existing data. Please refresh and try again.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        String m = e.getBindingResult().getFieldErrors().stream()
                .map(x -> x.getField() + ": " + x.getDefaultMessage())
                .findFirst().orElse("Validation error");
        return build(HttpStatus.BAD_REQUEST, m);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> constraintViolation(ConstraintViolationException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> runtime(RuntimeException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unhandled(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus s, String msg) {
        return ResponseEntity.status(s).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", s.value(),
                "error", msg));
    }
}
