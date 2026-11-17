package com.smartseason.agronomy.intelligence;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * The platform's catch-all turns every ResponseStatusException into a 500. An unavailable AI model
 * must reach the caller as a 503 with its message, so the screen can offer an agronomist instead.
 */
@RestControllerAdvice(assignableTypes = IntelligenceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IntelligenceErrors {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(ResponseStatusException ex) {
        HttpStatusCode code = ex.getStatusCode();
        return ResponseEntity.status(code).body(ProblemDetail.forStatusAndDetail(code, ex.getReason()));
    }
}
