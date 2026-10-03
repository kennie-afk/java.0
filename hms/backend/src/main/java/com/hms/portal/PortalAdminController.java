package com.hms.portal;

import static com.hms.portal.PortalModels.*;

import com.hms.platform.rbac.Permissions;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Staff side of the patient portal. */
@RestController
@RequestMapping("/v1/portal")
class PortalAdminController {

    private static final String MANAGE = "hasAuthority('" + Permissions.PORTAL_MANAGE + "')";
    private static final String RELEASE = "hasAuthority('" + Permissions.PORTAL_RELEASE + "')";

    private final PortalAdminService admin;

    PortalAdminController(PortalAdminService admin) {
        this.admin = admin;
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    Invitation invite(@Valid @RequestBody InviteInput in) {
        return admin.invite(in.patientId());
    }

    @GetMapping("/accounts/{patientId}")
    @PreAuthorize(MANAGE)
    AccountInfo account(@PathVariable UUID patientId) {
        return admin.account(patientId);
    }

    @GetMapping("/accounts/{patientId}/notifications")
    @PreAuthorize(MANAGE)
    List<com.hms.notify.NotificationService.Notice> notifications(@PathVariable UUID patientId) {
        return admin.notifications(patientId);
    }

    @PostMapping("/accounts/{patientId}/disable")
    @PreAuthorize(MANAGE)
    AccountInfo disable(@PathVariable UUID patientId) {
        return admin.setEnabled(patientId, false);
    }

    @PostMapping("/accounts/{patientId}/enable")
    @PreAuthorize(MANAGE)
    AccountInfo enable(@PathVariable UUID patientId) {
        return admin.setEnabled(patientId, true);
    }

    @PostMapping("/accounts/{patientId}/reset")
    @PreAuthorize(MANAGE)
    AccountInfo reset(@PathVariable UUID patientId) {
        admin.reset(patientId);
        return admin.account(patientId);
    }

    @GetMapping("/requests")
    @PreAuthorize(MANAGE)
    List<AppointmentRequest> requests(@RequestParam UUID facilityId, @RequestParam(required = false) String status) {
        return admin.requests(facilityId, status);
    }

    @PostMapping("/requests/{id}/resolve")
    @PreAuthorize(MANAGE)
    AppointmentRequest resolve(@PathVariable UUID id, @Valid @RequestBody ResolveInput in) {
        return admin.resolve(id, in);
    }

    @PostMapping("/lab-items/{id}/release")
    @PreAuthorize(RELEASE)
    Map<String, Object> releaseLab(@PathVariable UUID id) {
        admin.releaseLab(id, true);
        return Map.of("released", true);
    }

    @PostMapping("/lab-items/{id}/withdraw")
    @PreAuthorize(RELEASE)
    Map<String, Object> withdrawLab(@PathVariable UUID id) {
        admin.releaseLab(id, false);
        return Map.of("released", false);
    }

    @PostMapping("/imaging-orders/{id}/release")
    @PreAuthorize(RELEASE)
    Map<String, Object> releaseImaging(@PathVariable UUID id) {
        admin.releaseImaging(id, true);
        return Map.of("released", true);
    }

    @PostMapping("/imaging-orders/{id}/withdraw")
    @PreAuthorize(RELEASE)
    Map<String, Object> withdrawImaging(@PathVariable UUID id) {
        admin.releaseImaging(id, false);
        return Map.of("released", false);
    }
}
