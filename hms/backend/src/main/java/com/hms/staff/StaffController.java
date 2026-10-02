package com.hms.staff;

import static com.hms.staff.StaffModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
class StaffController {

    private static final String READ = "hasAuthority('" + Permissions.STAFF_READ + "')";
    private static final String MANAGE = "hasAuthority('" + Permissions.STAFF_MANAGE + "')";
    private static final String ROLES = "hasAuthority('" + Permissions.ROLES_MANAGE + "')";
    private static final String FACILITIES = "hasAuthority('" + Permissions.FACILITIES_MANAGE + "')";

    private final StaffService staff;

    StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping("/staff")
    @PreAuthorize(READ)
    Slice<StaffRow> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                         @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return staff.list(q, status, cursor, limit);
    }

    @GetMapping("/staff/{id}")
    @PreAuthorize(READ)
    Staff open(@PathVariable UUID id) {
        return staff.open(id);
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    Staff create(@Valid @RequestBody CreateStaff in) {
        return staff.create(in);
    }

    @PutMapping("/staff/{id}")
    @PreAuthorize(MANAGE)
    Staff update(@PathVariable UUID id, @Valid @RequestBody UpdateStaff in) {
        return staff.update(id, in);
    }

    @PutMapping("/staff/{id}/assignment")
    @PreAuthorize(MANAGE)
    Staff assignment(@PathVariable UUID id, @Valid @RequestBody Assignment in) {
        return staff.assignment(id, in);
    }

    @PostMapping("/staff/{id}/disable")
    @PreAuthorize(MANAGE)
    Staff disable(@PathVariable UUID id) {
        return staff.setStatus(id, false);
    }

    @PostMapping("/staff/{id}/enable")
    @PreAuthorize(MANAGE)
    Staff enable(@PathVariable UUID id) {
        return staff.setStatus(id, true);
    }

    @PostMapping("/staff/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE)
    void reset(@PathVariable UUID id, @Valid @RequestBody ResetPassword in) {
        staff.resetPassword(id, in);
    }

    @PostMapping("/auth/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePassword in) {
        staff.changeOwnPassword(in);
    }

    @GetMapping("/permissions")
    @PreAuthorize(READ)
    List<String> permissions() {
        return Permissions.ALL;
    }

    @GetMapping("/roles")
    @PreAuthorize(READ)
    List<Role> roles() {
        return staff.roles();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ROLES)
    Role createRole(@Valid @RequestBody RoleInput in) {
        return staff.createRole(in);
    }

    @PutMapping("/roles/{key}")
    @PreAuthorize(ROLES)
    Role updateRole(@PathVariable String key, @Valid @RequestBody RoleUpdate in) {
        return staff.updateRole(key, in);
    }

    @DeleteMapping("/roles/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(ROLES)
    void deleteRole(@PathVariable String key) {
        staff.deleteRole(key);
    }

    @GetMapping("/facilities")
    List<Facility> facilities() {
        return staff.facilities();
    }

    @PostMapping("/facilities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(FACILITIES)
    Facility createFacility(@Valid @RequestBody FacilityInput in) {
        return staff.createFacility(in);
    }

    @PutMapping("/facilities/{id}")
    @PreAuthorize(FACILITIES)
    Facility updateFacility(@PathVariable UUID id, @Valid @RequestBody FacilityUpdate in) {
        return staff.updateFacility(id, in);
    }
}
