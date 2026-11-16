package com.smartseason.weather.climate;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * The platform's catch-all turns every ResponseStatusException into a 500. For the climate
 * endpoint a bad location is the caller's fault (400) and an upstream outage is a 503.
 */
@RestControllerAdvice(assignableTypes = ClimateController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClimateErrors {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(ResponseStatusException ex) {
        HttpStatusCode code = ex.getStatusCode();
        return ResponseEntity.status(code).body(ProblemDetail.forStatusAndDetail(code, ex.getReason()));
    }
}
