package com.hms.programmes;

import static com.hms.programmes.ProgrammeModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/programmes")
class ProgrammeController {

    private static final String READ = "hasAuthority('" + Permissions.PROGRAMMES_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permissions.PROGRAMMES_WRITE + "')";

    private final ProgrammeService programmes;

    ProgrammeController(ProgrammeService programmes) {
        this.programmes = programmes;
    }

    @PostMapping("/enrolments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Enrolment enrol(@Valid @RequestBody EnrolInput in) {
        return programmes.enrol(in);
    }

    @GetMapping("/enrolments")
    @PreAuthorize(READ)
    Slice<Row> list(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) UUID patientId, @RequestParam(required = false) String programme,
                    @RequestParam(required = false) String status, @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return programmes.list(facilityId, patientId, programme, status, cursor, limit);
    }

    @GetMapping("/enrolments/{id}")
    @PreAuthorize(READ)
    Enrolment open(@PathVariable UUID id) {
        return programmes.open(id);
    }

    @PostMapping("/enrolments/{id}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Enrolment visit(@PathVariable UUID id, @Valid @RequestBody VisitInput in) {
        return programmes.visit(id, in);
    }

    @PostMapping("/enrolments/{id}/outcome")
    @PreAuthorize(WRITE)
    Enrolment outcome(@PathVariable UUID id, @Valid @RequestBody OutcomeInput in) {
        return programmes.outcome(id, in);
    }

    @GetMapping("/defaulters")
    @PreAuthorize(READ)
    List<Defaulter> defaulters(@RequestParam UUID facilityId, @RequestParam(required = false) String programme, @RequestParam(required = false) Integer graceDays) {
        return programmes.defaulters(facilityId, programme, graceDays);
    }

    @GetMapping("/summary")
    @PreAuthorize(READ)
    Summary summary(@RequestParam UUID facilityId, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                    @RequestParam(required = false) Integer graceDays) {
        return programmes.summary(facilityId, from, to, graceDays);
    }
}
