package com.smartseason.gateway;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/{service}")
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of(
                        "type", "https://docs.smartseason.io/errors/upstream-unavailable",
                        "title", "upstream-unavailable",
                        "status", 503,
                        "detail", service + " is not responding; the request was not applied",
                        "code", "upstream-unavailable",
                        "timestamp", Instant.now().toString()));
    }
}
