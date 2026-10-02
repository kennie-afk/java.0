package com.hms.clinical;

import static com.hms.clinical.ClinicalModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/clinical")
class ClinicalController {

    private static final String READ = "hasAuthority('" + Permissions.CLINICAL_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permissions.CLINICAL_WRITE + "')";
    private static final String ORDER = "hasAuthority('" + Permissions.ORDERS_WRITE + "')";

    private final ClinicalService clinical;

    ClinicalController(ClinicalService clinical) {
        this.clinical = clinical;
    }

    @PostMapping("/encounters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Encounter open(@Valid @RequestBody OpenEncounter in) {
        return clinical.open(in);
    }

    @GetMapping("/encounters")
    @PreAuthorize(READ)
    Slice<Encounter> list(@RequestParam(required = false) UUID patientId, @RequestParam(required = false) UUID facilityId,
                          @RequestParam(required = false) String status, @RequestParam(required = false) String cursor,
                          @RequestParam(required = false) Integer limit) {
        return clinical.list(patientId, facilityId, status, cursor, limit);
    }

    @GetMapping("/encounters/{id}")
    @PreAuthorize(READ)
    EncounterDetail detail(@PathVariable UUID id) {
        return clinical.detail(id);
    }

    @PostMapping("/encounters/{id}/triage")
    @PreAuthorize(WRITE)
    Encounter triage(@PathVariable UUID id, @Valid @RequestBody Triage in) {
        return clinical.triage(id, in);
    }

    @PostMapping("/encounters/{id}/close")
    @PreAuthorize(WRITE)
    Encounter close(@PathVariable UUID id, @Valid @RequestBody(required = false) CloseInput in) {
        return clinical.close(id, in);
    }

    @PostMapping("/encounters/{id}/vitals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Vitals addVitals(@PathVariable UUID id, @Valid @RequestBody VitalsInput in) {
        return clinical.addVitals(id, in);
    }

    @PostMapping("/vitals/{id}/retract")
    @PreAuthorize(WRITE)
    Vitals retract(@PathVariable UUID id, @Valid @RequestBody Retract in) {
        return clinical.retractVitals(id, in);
    }

    @PostMapping("/encounters/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Note addNote(@PathVariable UUID id, @Valid @RequestBody NoteInput in) {
        return clinical.addNote(id, in);
    }

    @PostMapping("/notes/{threadId}/amend")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Note amend(@PathVariable UUID threadId, @Valid @RequestBody Amend in) {
        return clinical.amendNote(threadId, in);
    }

    @GetMapping("/notes/{threadId}/history")
    @PreAuthorize(READ)
    List<Note> history(@PathVariable UUID threadId) {
        return clinical.noteHistory(threadId);
    }

    @PostMapping("/encounters/{id}/diagnoses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Diagnosis addDiagnosis(@PathVariable UUID id, @Valid @RequestBody DiagnosisInput in) {
        return clinical.addDiagnosis(id, in);
    }

    @PutMapping("/diagnoses/{id}")
    @PreAuthorize(WRITE)
    Diagnosis updateDiagnosis(@PathVariable UUID id, @Valid @RequestBody DiagnosisUpdate in) {
        return clinical.updateDiagnosis(id, in);
    }

    @GetMapping("/patients/{id}/allergies")
    @PreAuthorize(READ)
    List<Allergy> allergies(@PathVariable UUID id) {
        return clinical.allergiesOf(id);
    }

    @PostMapping("/patients/{id}/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Allergy addAllergy(@PathVariable UUID id, @Valid @RequestBody AllergyInput in) {
        return clinical.addAllergy(id, in);
    }

    @PutMapping("/allergies/{id}/status")
    @PreAuthorize(WRITE)
    Allergy allergyStatus(@PathVariable UUID id, @Valid @RequestBody AllergyStatus in) {
        return clinical.setAllergyStatus(id, in);
    }

    @PostMapping("/encounters/{id}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ORDER)
    Order addOrder(@PathVariable UUID id, @Valid @RequestBody OrderInput in) {
        return clinical.addOrder(id, in);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize(ORDER)
    Order cancelOrder(@PathVariable UUID id, @Valid @RequestBody CancelOrder in) {
        return clinical.cancelOrder(id, in);
    }
}
