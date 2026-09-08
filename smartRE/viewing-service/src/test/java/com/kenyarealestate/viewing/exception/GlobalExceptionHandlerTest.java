package com.kenyarealestate.viewing.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The handler is shared in shape across all eight services; testing it here covers the
 * decision, and the same file exists everywhere else.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("a request for a path that does not exist is 404, not a 500 that pages somebody")
    void unknownRouteIsNotFound() {
        ResponseEntity<Map<String, Object>> res =
                handler.noSuchRoute(new NoResourceFoundException(HttpMethod.GET, "/api/viewings/my"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).containsEntry("status", 404);
    }

    @Test
    @DisplayName("the body does not echo the probed path back, so a scanner learns nothing from a miss")
    void bodyDoesNotLeakTheProbedPath() {
        ResponseEntity<Map<String, Object>> res =
                handler.noSuchRoute(new NoResourceFoundException(HttpMethod.GET, "/api/internal/secrets"));

        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().toString()).doesNotContain("secrets");
    }

    @Test
    @DisplayName("a genuine server fault is still a 500 — this must not quieten real failures")
    void realFailureStillFiveHundred() {
        ResponseEntity<Map<String, Object>> res = handler.unhandled(new IllegalStateException("boom"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).containsEntry("status", 500);
    }

    @Test
    @DisplayName("a 500 body says nothing about the cause — the detail belongs in the log, not the response")
    void serverFaultDoesNotLeakInternals() {
        ResponseEntity<Map<String, Object>> res =
                handler.unhandled(new IllegalStateException("jdbc:postgresql://user-db:5432 refused"));

        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().toString()).doesNotContain("postgresql");
    }
}
