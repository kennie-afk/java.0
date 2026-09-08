package com.kenyarealestate.notification.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        return build(HttpStatus.NOT_FOUND, "No such endpoint.");
    }
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String,Object>> notFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String,Object>> forbidden(ForbiddenException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException e) {
        String m = e.getBindingResult().getFieldErrors().stream()
                .map(x -> x.getField() + ": " + x.getDefaultMessage())
                .findFirst().orElse("Validation error");
        return build(HttpStatus.BAD_REQUEST, m);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String,Object>> runtime(RuntimeException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<Map<String,Object>> build(HttpStatus s, String msg) {
        return ResponseEntity.status(s).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", s.value(),
                "error", msg == null ? s.getReasonPhrase() : msg));
    }
}
