package com.mara.identity.staff;

import com.mara.platform.staff.SignInOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A terminal asks whether a member of staff may sign in. Like enrolment, this is reachable
 * with no tenant context and so answers uninformatively: every refusal except a lockout is
 * the same status and the same body.
 */
@RestController
@RequestMapping("/v1/terminals/{terminalId}/staff-signin")
public class StaffSignInController {

    private final StaffSignInService service;

    public StaffSignInController(StaffSignInService service) {
        this.service = service;
    }

    public record SignInRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{1,20}$") String staffNumber,
            @NotBlank @Size(max = 12) String pin,
            long timestamp,
            @NotBlank @Size(max = 200) String signature) {
    }

    @PostMapping
    public ResponseEntity<?> signIn(@PathVariable String terminalId, @Valid @RequestBody SignInRequest r) {
        StaffSignInService.Result result =
                service.signIn(terminalId, r.staffNumber(), r.pin(), r.timestamp(), r.signature());
        SignInOutcome outcome = result.outcome();

        if (outcome.succeeded()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("staffId", result.staffId());
            body.put("staffNumber", result.staffNumber());
            body.put("displayName", result.displayName());
            body.put("role", result.role());
            body.put("branchId", result.branchId());
            return ResponseEntity.ok(body);
        }
        if (outcome.outcome() == SignInOutcome.Result.LOCKED) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(Map.of("error", "locked", "lockedUntil", outcome.lockedUntil().toString()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "signin_refused", "message", "Staff number or PIN not accepted."));
    }
}
