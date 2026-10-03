package com.mara.identity.admin;

import com.mara.identity.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator provisioning, behind the credential filter (scopes {@code admin:read}, {@code admin:write}, {@code platform:tenants}). Creating a tenant needs no
 * {@code X-Mara-Tenant} (the tenant does not exist yet); every other call names the tenant
 * it acts on through that header, like the rest of this service.
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final ProvisioningService service;

    public AdminController(ProvisioningService service) {
        this.service = service;
    }

    public record CreateTenant(
            @NotBlank @Size(max = 120) String legalName,
            @NotBlank @Size(max = 120) String tradingName,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
            @Size(max = 40) String taxIdentifier,
            @Min(1) @Max(500) int licensedTerminals,
            @NotBlank @Size(max = 80) String branchName,
            @NotBlank @Size(max = 60) String timezone,
            @NotBlank @Size(max = 80) String ownerName,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{1,20}$") String ownerStaffNumber,
            @NotBlank String ownerPin) {
    }

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@Valid @RequestBody CreateTenant r) {
        var created = service.createTenant(
                r.legalName(), r.tradingName(), r.countryCode(), r.currency(), r.taxIdentifier(),
                r.licensedTerminals(), r.branchName(), r.timezone(), r.ownerName(), r.ownerStaffNumber(), r.ownerPin());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    public record CreateBranch(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 60) String timezone) {
    }

    @PostMapping("/branches")
    public ResponseEntity<?> createBranch(@Valid @RequestBody CreateBranch r) {
        String id = service.createBranch(TenantContext.require(), r.name(), r.timezone());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("branchId", id));
    }

    public record CreateStaff(
            String branchId,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank String role,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{1,20}$") String staffNumber,
            @NotBlank String pin) {
    }

    @PostMapping("/staff")
    public ResponseEntity<?> createStaff(@Valid @RequestBody CreateStaff r) {
        String id = service.createStaff(TenantContext.require(), r.branchId(), r.displayName(), r.role(), r.staffNumber(), r.pin());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("staffId", id));
    }

    public record IssueCode(@NotBlank String branchId, @NotBlank String issuedBy) {
    }

    /** The one response that contains a plaintext code. It cannot be fetched again. */
    @PostMapping("/enrolment-codes")
    public ResponseEntity<?> issueCode(@Valid @RequestBody IssueCode r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.issueEnrolmentCode(TenantContext.require(), r.branchId(), r.issuedBy()));
    }

    public record SetStatus(@NotBlank @Pattern(regexp = "^(ACTIVE|SUSPENDED|REVOKED)$") String status) {
    }

    @PostMapping("/staff/{id}/status")
    public ResponseEntity<?> staffStatus(@PathVariable String id, @Valid @RequestBody SetStatus r) {
        service.setStaffStatus(TenantContext.require(), id, r.status());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/terminals/{id}/status")
    public ResponseEntity<?> terminalStatus(@PathVariable String id, @Valid @RequestBody SetStatus r) {
        service.setTerminalStatus(TenantContext.require(), id, r.status());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staff")
    public List<Map<String, Object>> staff() {
        return service.listStaff(TenantContext.require());
    }

    @GetMapping("/terminals")
    public List<Map<String, Object>> terminals() {
        return service.listTerminals(TenantContext.require());
    }

    @GetMapping("/branches")
    public List<Map<String, Object>> branches() {
        return service.listBranches(TenantContext.require());
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "100") int limit) {
        return service.auditTrail(TenantContext.require(), limit);
    }

    @ExceptionHandler(ProvisioningService.Refused.class)
    public ResponseEntity<?> refused(ProvisioningService.Refused e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "refused", "message", e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> duplicate(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "duplicate",
                "message", "That staff number (or value) is already in use."));
    }
}
