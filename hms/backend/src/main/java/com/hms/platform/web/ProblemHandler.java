package com.hms.platform.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** One error shape for the whole API (RFC 9457 problem details), with a stable `code`. */
@RestControllerAdvice
class ProblemHandler {
    private static final Logger log = LoggerFactory.getLogger(ProblemHandler.class);

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, Map<String, ?> extra) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        p.setProperty("code", code);
        if (extra != null && !extra.isEmpty()) {
            p.setProperty("fields", extra);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }

    @ExceptionHandler(PossibleDuplicateException.class)
    ResponseEntity<ProblemDetail> possibleDuplicate(PossibleDuplicateException e) {
        ResponseEntity<ProblemDetail> response = problem(e.status(), e.code(), e.getMessage(), null);
        response.getBody().setProperty("candidates", e.candidates());
        return response;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e) {
        return problem(e.status(), e.code(), e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "validation_failed", "The request has invalid fields.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "malformed_request", "The request could not be read.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Your role does not allow this.", null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ProblemDetail> duplicate(DuplicateKeyException e) {
        return problem(HttpStatus.CONFLICT, "duplicate", "That record already exists.", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("unhandled error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Something went wrong on our side.", null);
    }
}
