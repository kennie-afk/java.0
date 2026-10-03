package com.hms.portal;

import static com.hms.portal.PortalModels.*;

import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** The patient's own view. Authorised by a portal session only; the patient is the one in the session, never a request parameter. */
@RestController
@RequestMapping("/portal")
class PortalController {

    private final PortalService portal;

    PortalController(PortalService portal) {
        this.portal = portal;
    }

    @GetMapping("/me")
    Me me() {
        return portal.profile();
    }

    @GetMapping("/results")
    Slice<LabResult> results(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return portal.results(cursor, limit);
    }

    @GetMapping("/imaging")
    List<ImagingReport> imaging() {
        return portal.imaging();
    }

    @GetMapping("/appointments")
    List<Appointment> appointments() {
        return portal.appointments();
    }

    @GetMapping("/medications")
    List<Medication> medications() {
        return portal.medications();
    }

    @GetMapping("/allergies")
    List<Allergy> allergies() {
        return portal.allergies();
    }

    @GetMapping("/facilities")
    List<FacilityRef> facilities() {
        return portal.facilities();
    }

    @GetMapping("/appointment-requests")
    List<AppointmentRequest> requests() {
        return portal.requests();
    }

    @PostMapping("/appointment-requests")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentRequest request(@Valid @RequestBody RequestInput in) {
        return portal.request(in);
    }

    @PostMapping("/appointment-requests/{id}/cancel")
    AppointmentRequest cancel(@PathVariable UUID id) {
        return portal.cancel(id);
    }
}
