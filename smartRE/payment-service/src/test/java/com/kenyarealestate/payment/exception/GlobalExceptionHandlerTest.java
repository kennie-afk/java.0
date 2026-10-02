package com.kenyarealestate.payment.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void getOnPostOnlyPathIsMethodNotAllowedNotServerError() {
        ResponseEntity<Map<String, Object>> res =
                handler.wrongMethod(new HttpRequestMethodNotSupportedException("GET", List.of("POST")));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody()).containsEntry("status", 405);
        assertThat(res.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
    }
}
