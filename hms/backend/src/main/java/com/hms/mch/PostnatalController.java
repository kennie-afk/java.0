package com.hms.mch;

import static com.hms.mch.PostnatalModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/mch")
class PostnatalController {

    private static final String READ = "hasAuthority('" + Permissions.MCH_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permissions.MCH_WRITE + "')";

    private final PostnatalService postnatal;
    private final FamilyPlanningService familyPlanning;

    PostnatalController(PostnatalService postnatal, FamilyPlanningService familyPlanning) {
        this.postnatal = postnatal;
        this.familyPlanning = familyPlanning;
    }

    @GetMapping("/pregnancies/{id}/postnatal")
    @PreAuthorize(READ)
    Postnatal postnatal(@PathVariable UUID id) {
        return postnatal.get(id);
    }

    @PostMapping("/pregnancies/{id}/postnatal")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    Postnatal addPostnatal(@PathVariable UUID id, @Valid @RequestBody PostnatalVisitInput in) {
        return postnatal.addVisit(id, in);
    }

    @GetMapping("/family-planning/patients/{patientId}")
    @PreAuthorize(READ)
    FamilyPlanning familyPlanning(@PathVariable UUID patientId) {
        return familyPlanning.get(patientId);
    }

    @PostMapping("/family-planning/patients/{patientId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    FamilyPlanning recordFamilyPlanning(@PathVariable UUID patientId, @Valid @RequestBody FamilyPlanningInput in) {
        return familyPlanning.record(patientId, in);
    }

    @GetMapping("/family-planning/due")
    @PreAuthorize(READ)
    Slice<FamilyPlanningDue> familyPlanningDue(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) Integer horizonDays,
                                               @RequestParam(required = false) Boolean overdueOnly, @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        return familyPlanning.due(facilityId, horizonDays, overdueOnly, cursor, limit);
    }
}
