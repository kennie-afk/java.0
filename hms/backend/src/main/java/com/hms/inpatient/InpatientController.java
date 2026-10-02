package com.hms.inpatient;

import static com.hms.inpatient.InpatientModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/inpatient")
class InpatientController {

    private static final String READ = "hasAuthority('" + Permissions.INPATIENT_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permissions.INPATIENT_WRITE + "')";
    private static final String SETUP = "hasAuthority('" + Permissions.FACILITIES_MANAGE + "')";

    private final InpatientService inpatient;

    InpatientController(InpatientService inpatient) {
        this.inpatient = inpatient;
    }

    @GetMapping("/wards")
    @PreAuthorize(READ)
    List<Ward> wards(@RequestParam UUID facilityId) {
        return inpatient.wards(facilityId);
    }

    @PostMapping("/wards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SETUP)
    Ward createWard(@Valid @RequestBody WardInput in) {
        return inpatient.createWard(in);
    }

    @PostMapping("/wards/{id}/beds")
    @PreAuthorize(SETUP)
    Ward addBeds(@PathVariable UUID id, @Valid @RequestBody BedsInput in) {
        return inpatient.addBeds(id, in);
    }

    @GetMapping("/wards/{id}/beds")
    @PreAuthorize(READ)
    List<Bed> beds(@PathVariable UUID id) {
        return inpatient.beds(id);
    }

    @PutMapping("/beds/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    void bedStatus(@PathVariable UUID id, @Valid @RequestBody BedStatus in) {
        inpatient.setBedStatus(id, in);
    }

    @PostMapping("/admissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Admission admit(@Valid @RequestBody AdmitInput in) {
        return inpatient.admit(in);
    }

    @GetMapping("/admissions")
    @PreAuthorize(READ)
    Slice<Admission> list(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) String status, @RequestParam(required = false) UUID patientId,
                          @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return inpatient.list(facilityId, status, patientId, cursor, limit);
    }

    @GetMapping("/admissions/{id}")
    @PreAuthorize(READ)
    Admission open(@PathVariable UUID id) {
        return inpatient.open(id);
    }

    @PostMapping("/admissions/{id}/transfer")
    @PreAuthorize(WRITE)
    Admission transfer(@PathVariable UUID id, @Valid @RequestBody TransferInput in) {
        return inpatient.transfer(id, in);
    }

    @PostMapping("/admissions/{id}/discharge")
    @PreAuthorize(WRITE)
    Admission discharge(@PathVariable UUID id, @Valid @RequestBody DischargeInput in) {
        return inpatient.discharge(id, in);
    }
}
