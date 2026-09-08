package com.mara.identity.enrolment;

import com.mara.platform.identity.EnrolmentOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint a device may call before it belongs to anyone.
 *
 * <p>Runs without a tenant context by design — see {@code TenantFilter.UNSCOPED_PATHS}.
 * That makes it the most exposed surface in the service, so the response is deliberately
 * uninformative: every refusal returns the same status and the same body. A caller
 * learns that enrolment failed and nothing more, because "wrong code" versus "expired"
 * versus "already used" is a search procedure when you can ask repeatedly.
 */
@RestController
@RequestMapping("/v1/enrolment")
public class EnrolmentController {

    private final EnrolmentService service;

    public EnrolmentController(EnrolmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> enrol(@Valid @RequestBody EnrolRequest request) {
        EnrolmentOutcome outcome = service.enrol(request.code(), request.publicKey(), request.label());

        if (!outcome.accepted()) {
            // One shape for every rejection. The specific reason went to the audit log,
            // which is behind authentication this caller does not have.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new Refused("enrolment_refused",
                            "This code cannot be used to enrol a terminal."));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Enrolled(outcome.terminalId()));
    }

    /**
     * @param code      the enrolment code, however the operator typed it
     * @param publicKey X.509 SubjectPublicKeyInfo, base64 — generated on the device,
     *                  whose private half never leaves it
     * @param label     what the owner will see: "Lane 4", "Front desk"
     */
    public record EnrolRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 200) String publicKey,
            @NotBlank @Size(max = 60) String label) {
    }

    public record Enrolled(String terminalId) {
    }

    public record Refused(String error, String message) {
    }
}
