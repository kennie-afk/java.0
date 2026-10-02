package com.hms.scheduling;

import static com.hms.scheduling.SchedulingModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/scheduling")
class SchedulingController {

    private static final String READ = "hasAuthority('" + Permissions.SCHEDULING_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permissions.SCHEDULING_WRITE + "')";
    private static final String FACILITIES = "hasAuthority('" + Permissions.FACILITIES_MANAGE + "')";

    private final SchedulingService scheduling;

    SchedulingController(SchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @GetMapping("/clinics")
    @PreAuthorize(READ)
    List<Clinic> clinics(@RequestParam(required = false) UUID facilityId) {
        return scheduling.clinics(facilityId);
    }

    @PostMapping("/clinics")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(FACILITIES)
    Clinic createClinic(@Valid @RequestBody ClinicInput in) {
        return scheduling.createClinic(in);
    }

    @PutMapping("/clinics/{id}")
    @PreAuthorize(FACILITIES)
    Clinic updateClinic(@PathVariable UUID id, @Valid @RequestBody ClinicUpdate in) {
        return scheduling.updateClinic(id, in);
    }

    @GetMapping("/slots")
    @PreAuthorize(READ)
    List<Slot> slots(@RequestParam UUID clinicId, @RequestParam(required = false) UUID practitionerId,
                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduling.slots(clinicId, practitionerId, date);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Appointment book(@Valid @RequestBody BookInput in) {
        return scheduling.book(in);
    }

    @PostMapping("/walk-ins")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Appointment walkIn(@Valid @RequestBody WalkInInput in) {
        return scheduling.walkIn(in);
    }

    @GetMapping("/appointments")
    @PreAuthorize(READ)
    Slice<Appointment> list(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) UUID patientId,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return scheduling.list(facilityId, patientId, status, from, to, cursor, limit);
    }

    @GetMapping("/appointments/{id}")
    @PreAuthorize(READ)
    Appointment open(@PathVariable UUID id) {
        return scheduling.open(id);
    }

    @PostMapping("/appointments/{id}/reschedule")
    @PreAuthorize(WRITE)
    Appointment reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleInput in) {
        return scheduling.reschedule(id, in);
    }

    @PostMapping("/appointments/{id}/check-in")
    @PreAuthorize(WRITE)
    Appointment checkIn(@PathVariable UUID id) {
        return scheduling.transition(id, "CHECKED_IN", null);
    }

    @PostMapping("/appointments/{id}/start")
    @PreAuthorize(WRITE)
    Appointment start(@PathVariable UUID id) {
        return scheduling.transition(id, "IN_PROGRESS", null);
    }

    @PostMapping("/appointments/{id}/complete")
    @PreAuthorize(WRITE)
    Appointment complete(@PathVariable UUID id) {
        return scheduling.transition(id, "COMPLETED", null);
    }

    @PostMapping("/appointments/{id}/no-show")
    @PreAuthorize(WRITE)
    Appointment noShow(@PathVariable UUID id) {
        return scheduling.transition(id, "NO_SHOW", null);
    }

    @PostMapping("/appointments/{id}/cancel")
    @PreAuthorize(WRITE)
    Appointment cancel(@PathVariable UUID id, @Valid @RequestBody CancelInput in) {
        return scheduling.transition(id, "CANCELLED", in.reason().trim());
    }

    @PutMapping("/appointments/{id}/priority")
    @PreAuthorize(WRITE)
    Appointment priority(@PathVariable UUID id, @Valid @RequestBody PriorityInput in) {
        return scheduling.setPriority(id, in.priority());
    }

    @GetMapping("/queue")
    @PreAuthorize(READ)
    List<Appointment> queue(@RequestParam UUID facilityId, @RequestParam(required = false) UUID clinicId) {
        return scheduling.queue(facilityId, clinicId);
    }
}
