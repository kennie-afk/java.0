package com.hms.claims;

import static com.hms.claims.ClaimModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/claims")
class ClaimsController {

    private static final String READ = "hasAuthority('" + Permissions.CLAIMS_READ + "')";
    private static final String SUBMIT = "hasAuthority('" + Permissions.CLAIMS_SUBMIT + "')";

    private final ClaimsService claims;

    ClaimsController(ClaimsService claims) {
        this.claims = claims;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SUBMIT)
    Claim assemble(@Valid @RequestBody AssembleInput in) {
        return claims.assemble(in);
    }

    @GetMapping
    @PreAuthorize(READ)
    Slice<Row> list(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) String status, @RequestParam(required = false) UUID patientId,
                    @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return claims.list(facilityId, status, patientId, cursor, limit);
    }

    @GetMapping("/summary")
    @PreAuthorize(READ)
    Summary summary(@RequestParam UUID facilityId) {
        return claims.summary(facilityId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    Claim open(@PathVariable UUID id) {
        return claims.open(id);
    }

    @PostMapping("/{id}/reassemble")
    @PreAuthorize(SUBMIT)
    Claim reassemble(@PathVariable UUID id) {
        return claims.reassemble(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(SUBMIT)
    Claim submit(@PathVariable UUID id) {
        return claims.submit(id);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(SUBMIT)
    Claim withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawInput in) {
        return claims.withdraw(id, in);
    }
}
