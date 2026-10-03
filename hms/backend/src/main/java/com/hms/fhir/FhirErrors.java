package com.hms.fhir;

import static com.hms.fhir.FhirService.obj;

import com.hms.platform.web.ApiException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errors from the FHIR interface are OperationOutcome resources, not the problem-details shape the rest of the API uses. */
@RestControllerAdvice(assignableTypes = FhirController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class FhirErrors {
    private static final Logger log = LoggerFactory.getLogger(FhirErrors.class);

    private static ResponseEntity<Map<String, Object>> outcome(HttpStatus status, String code, String text) {
        Map<String, Object> o = obj("resourceType", "OperationOutcome", "issue", List.of(obj("severity", "error", "code", code, "diagnostics", text)));
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType(FhirController.FHIR_JSON)).body(o);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        String code = switch (e.status()) {
            case NOT_FOUND -> "not-found";
            case FORBIDDEN -> "forbidden";
            case CONFLICT -> "conflict";
            default -> "invalid";
        };
        return outcome(e.status(), code, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
        return outcome(HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to read this.");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> bad(Exception e) {
        return outcome(HttpStatus.BAD_REQUEST, "invalid", "A search parameter is missing or malformed.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> other(Exception e) {
        log.error("Unhandled FHIR error", e);
        return outcome(HttpStatus.INTERNAL_SERVER_ERROR, "exception", "Something went wrong on our side.");
    }
}
