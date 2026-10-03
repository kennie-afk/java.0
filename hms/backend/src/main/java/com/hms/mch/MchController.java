package com.hms.mch;

import static com.hms.mch.MchModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/mch")
class MchController {

    private static final String READ = "hasAuthority('" + Permissions.MCH_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permissions.MCH_WRITE + "')";

    private final MchService mch;
    private final MchSummaryService summary;

    MchController(MchService mch, MchSummaryService summary) {
        this.mch = mch;
        this.summary = summary;
    }

    @PostMapping("/pregnancies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Pregnancy open(@Valid @RequestBody PregnancyInput in) {
        return mch.open(in);
    }

    @GetMapping("/pregnancies")
    @PreAuthorize(READ)
    Slice<Pregnancy> list(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) String status,
                          @RequestParam(required = false) UUID patientId, @RequestParam(required = false) Boolean overdue,
                          @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return mch.list(facilityId, status, patientId, overdue, cursor, limit);
    }

    @GetMapping("/pregnancies/{id}")
    @PreAuthorize(READ)
    Pregnancy get(@PathVariable UUID id) {
        return mch.get(id);
    }

    @PostMapping("/pregnancies/{id}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Pregnancy visit(@PathVariable UUID id, @Valid @RequestBody AncVisitInput in) {
        return mch.addVisit(id, in);
    }

    @PostMapping("/pregnancies/{id}/delivery")
    @PreAuthorize(WRITE)
    Pregnancy deliver(@PathVariable UUID id, @Valid @RequestBody DeliveryInput in) {
        return mch.deliver(id, in);
    }

    @GetMapping("/immunisation/patients/{patientId}")
    @PreAuthorize(READ)
    Card card(@PathVariable UUID patientId) {
        return mch.card(patientId);
    }

    @PostMapping("/immunisation/patients/{patientId}/doses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Card give(@PathVariable UUID patientId, @Valid @RequestBody DoseInput in) {
        return mch.give(patientId, in);
    }

    @GetMapping("/immunisation/due")
    @PreAuthorize(READ)
    Slice<DueDose> due(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) Integer horizonDays,
                       @RequestParam(required = false) Boolean overdueOnly, @RequestParam(required = false) String cursor,
                       @RequestParam(required = false) Integer limit) {
        return mch.due(facilityId, horizonDays, overdueOnly, cursor, limit);
    }

    @GetMapping("/summary")
    @PreAuthorize(READ)
    java.util.Map<String, Object> summary(@RequestParam UUID facilityId,
                                          @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                                          @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return summary.summary(facilityId, from, to);
    }
}
