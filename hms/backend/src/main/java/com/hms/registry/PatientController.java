package com.hms.registry;

import static com.hms.registry.PatientModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/patients")
class PatientController {

    private final PatientService patients;

    PatientController(PatientService patients) {
        this.patients = patients;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.PATIENTS_WRITE + "')")
    Patient register(@Valid @RequestBody CreatePatient in) {
        return patients.register(in);
    }

    record DuplicateCheck(@jakarta.validation.constraints.NotNull @Valid Demographics demographics, List<@Valid IdentifierInput> identifiers) {}

    @PostMapping("/duplicate-check")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENTS_WRITE + "')")
    List<Match> duplicateCheck(@Valid @RequestBody DuplicateCheck in) {
        return patients.duplicates(in.demographics(), in.identifiers());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PATIENTS_READ + "')")
    Slice<Summary> search(@RequestParam(required = false) String q, @RequestParam(required = false) String cursor,
                          @RequestParam(required = false) Integer limit) {
        return patients.search(q, cursor, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENTS_READ + "')")
    Patient open(@PathVariable UUID id, @RequestHeader(name = "X-Access-Reason", required = false) String reason) {
        return patients.open(id, reason);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENTS_WRITE + "')")
    Patient update(@PathVariable UUID id, @Valid @RequestBody UpdatePatient in) {
        return patients.update(id, in);
    }

    @PostMapping("/{id}/identifiers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.PATIENTS_WRITE + "')")
    Identifier addIdentifier(@PathVariable UUID id, @Valid @RequestBody IdentifierInput in) {
        return patients.addIdentifier(id, in);
    }

    @PostMapping("/{id}/merge")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENTS_MERGE + "')")
    Patient merge(@PathVariable UUID id, @Valid @RequestBody MergeRequest in) {
        return patients.merge(id, in);
    }
}
