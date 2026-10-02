package com.hms.staff;

import static com.hms.staff.StaffModels.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.AccessService;
import com.hms.platform.rbac.DefaultRoles;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff, roles and facilities administration. The rule that runs through all of it: nobody can give
 * away more than they hold. An administrator of one ward cannot mint a role, or assign a site, that
 * lifts anybody (themselves included) above their own reach.
 */
@Service
public class StaffService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final AccessService access;
    private final ObjectMapper json;

    public StaffService(JdbcClient jdbc, AuditService audit, PasswordEncoder encoder, AccessService access, ObjectMapper json) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.encoder = encoder;
        this.access = access;
        this.json = json;
    }

    // ---- staff -------------------------------------------------------------------------------

    @Transactional
    public Staff create(CreateStaff in) {
        TenantContext.Tenant t = TenantContext.require();
        Set<String> roles = checkRoles(t, in.roles());
        Set<UUID> facilities = checkFacilities(t, in.facilityIds());
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO practitioners (org_id, email, password_hash, full_name, cadre, licence_body, licence_no, phone, must_change_password)
                    VALUES (?, ?::citext, ?, ?, ?, ?, ?, ?, true) RETURNING id""")
                    .params(t.orgId(), in.email().trim(), encoder.encode(in.temporaryPassword()), in.fullName().trim(), in.cadre(),
                            in.licenceBody(), blank(in.licenceNo()), blank(in.phone()))
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("email_taken", "That email address is already registered.");
        }
        assign(t, id, roles, facilities);
        audit.record("staff.create", "practitioner", id, null, null, Map.of("roles", String.join(",", roles), "cadre", in.cadre()));
        access.invalidateAll();
        return load(id);
    }

    @Transactional
    public Staff update(UUID id, UpdateStaff in) {
        TenantContext.Tenant t = TenantContext.require();
        load(id);
        jdbc.sql("UPDATE practitioners SET full_name = ?, cadre = ?, licence_body = ?, licence_no = ?, phone = ?, updated_at = now() WHERE org_id = ? AND id = ?")
                .params(in.fullName().trim(), in.cadre(), in.licenceBody(), blank(in.licenceNo()), blank(in.phone()), t.orgId(), id).update();
        audit.record("staff.update", "practitioner", id, null, null, Map.of());
        return load(id);
    }

    @Transactional
    public Staff assignment(UUID id, Assignment in) {
        TenantContext.Tenant t = TenantContext.require();
        Staff before = load(id);
        Set<String> roles = checkRoles(t, in.roles());
        Set<UUID> facilities = checkFacilities(t, in.facilityIds());
        if (before.roles().contains(DefaultRoles.ADMIN) && !roles.contains(DefaultRoles.ADMIN)) {
            requireAnotherAdmin(id);
        }
        // Roles the caller could not themselves grant stay as they are: a limited admin cannot strip a
        // role they have no right to manage.
        Set<String> keep = new LinkedHashSet<>(roles);
        for (String existing : before.roles()) {
            if (!canGrant(t, existing)) {
                keep.add(existing);
            }
        }
        jdbc.sql("DELETE FROM practitioner_roles WHERE org_id = ? AND practitioner_id = ?").params(t.orgId(), id).update();
        jdbc.sql("DELETE FROM practitioner_facilities WHERE org_id = ? AND practitioner_id = ?").params(t.orgId(), id).update();
        assign(t, id, keep, facilities);
        audit.record("staff.assign", "practitioner", id, null, null,
                Map.of("roles", String.join(",", keep), "facilities", facilities.size()));
        access.invalidateAll();
        return load(id);
    }

    @Transactional
    public Staff setStatus(UUID id, boolean active) {
        TenantContext.Tenant t = TenantContext.require();
        Staff s = load(id);
        if (!active) {
            if (id.equals(t.practitionerId())) {
                throw ApiException.conflict("self_disable", "You cannot disable your own account.");
            }
            if (s.roles().contains(DefaultRoles.ADMIN)) {
                requireAnotherAdmin(id);
            }
        }
        jdbc.sql("UPDATE practitioners SET status = ?, failed_logins = 0, locked_until = NULL, updated_at = now() WHERE org_id = ? AND id = ?")
                .params(active ? "ACTIVE" : "DISABLED", t.orgId(), id).update();
        audit.record(active ? "staff.enable" : "staff.disable", "practitioner", id, null, null, Map.of());
        access.invalidateAll();
        return load(id);
    }

    @Transactional
    public void resetPassword(UUID id, ResetPassword in) {
        TenantContext.Tenant t = TenantContext.require();
        load(id);
        jdbc.sql("UPDATE practitioners SET password_hash = ?, must_change_password = true, failed_logins = 0, locked_until = NULL, updated_at = now() WHERE org_id = ? AND id = ?")
                .params(encoder.encode(in.temporaryPassword()), t.orgId(), id).update();
        audit.record("staff.password.reset", "practitioner", id, null, null, Map.of());
    }

    @Transactional
    public void changeOwnPassword(ChangePassword in) {
        TenantContext.Tenant t = TenantContext.require();
        String hash = jdbc.sql("SELECT password_hash FROM practitioners WHERE org_id = ? AND id = ?").params(t.orgId(), t.practitionerId())
                .query(String.class).single();
        if (!encoder.matches(in.currentPassword(), hash)) {
            throw ApiException.badRequest("wrong_password", "The current password is not right.");
        }
        if (in.currentPassword().equals(in.newPassword())) {
            throw ApiException.badRequest("same_password", "Choose a password you have not used just now.");
        }
        jdbc.sql("UPDATE practitioners SET password_hash = ?, must_change_password = false, password_changed_at = now(), updated_at = now() WHERE org_id = ? AND id = ?")
                .params(encoder.encode(in.newPassword()), t.orgId(), t.practitionerId()).update();
        audit.record("staff.password.change", "practitioner", t.practitionerId(), null, null, Map.of());
    }

    @Transactional(readOnly = true)
    public Slice<StaffRow> list(String q, String status, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        Map<String, String> after = Slice.decode(cursor);
        List<Object> p = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, email, full_name, cadre, status, licence_no, lower(full_name) AS sort FROM practitioners WHERE org_id = ?");
        p.add(t.orgId());
        if (q != null && !q.isBlank()) {
            sql.append(" AND (lower(full_name) LIKE ? OR lower(email::text) LIKE ?)");
            String like = "%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%";
            p.add(like);
            p.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            p.add(status);
        }
        if (after != null) {
            sql.append(" AND (lower(full_name), id) > (?, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY lower(full_name), id LIMIT ?");
        p.add(size + 1);
        List<Object[]> rows = jdbc.sql(sql.toString()).params(p.toArray())
                .query((rs, n) -> new Object[] {new StaffRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("full_name"),
                        rs.getString("cadre"), rs.getString("status"), rs.getString("licence_no")), rs.getString("sort")}).list();
        boolean more = rows.size() > size;
        List<Object[]> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", (String) page.get(page.size() - 1)[1], "i", ((StaffRow) page.get(page.size() - 1)[0]).id().toString())) : null;
        return new Slice<>(page.stream().map(r -> (StaffRow) r[0]).toList(), next);
    }

    @Transactional(readOnly = true)
    public Staff open(UUID id) {
        return load(id);
    }

    // ---- roles -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Role> roles() {
        TenantContext.Tenant t = TenantContext.require();
        return jdbc.sql("""
                SELECT r.role_key, r.label, r.description, r.is_system, r.permissions::text AS permissions,
                       (SELECT count(*) FROM practitioner_roles pr WHERE pr.org_id = r.org_id AND pr.role_key = r.role_key) AS members
                  FROM roles r WHERE r.org_id = ? ORDER BY r.is_system DESC, r.role_key""").param(t.orgId())
                .query((rs, n) -> new Role(rs.getString("role_key"), rs.getString("label"), rs.getString("description"), rs.getBoolean("is_system"),
                        permissionsOf(rs.getString("permissions"), rs.getString("role_key")), rs.getLong("members"))).list();
    }

    @Transactional
    public Role createRole(RoleInput in) {
        TenantContext.Tenant t = TenantContext.require();
        List<String> perms = checkPermissions(t, in.permissions());
        try {
            jdbc.sql("INSERT INTO roles (org_id, role_key, label, description, is_system, permissions) VALUES (?, ?, ?, ?, false, ?::jsonb)")
                    .params(t.orgId(), in.key(), in.label().trim(), blank(in.description()), toJson(perms)).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("role_exists", "A role with that key already exists.");
        }
        audit.record("role.create", "role", in.key(), null, null, Map.of("permissions", perms.size()));
        return role(in.key());
    }

    @Transactional
    public Role updateRole(String key, RoleUpdate in) {
        TenantContext.Tenant t = TenantContext.require();
        Role existing = role(key);
        if (DefaultRoles.ADMIN.equals(key)) {
            throw ApiException.conflict("admin_role_fixed", "The administrator role is always everything and cannot be edited.");
        }
        List<String> perms = checkPermissions(t, in.permissions());
        // Removing a permission someone else holds is fine; the caller may only edit a role whose
        // existing permissions they hold entirely, so they cannot reshape a role above their own level.
        if (!t.permissions().containsAll(existing.permissions())) {
            throw ApiException.forbidden("That role holds permissions you do not have, so you cannot change it.");
        }
        jdbc.sql("UPDATE roles SET label = ?, description = ?, permissions = ?::jsonb WHERE org_id = ? AND role_key = ?")
                .params(in.label().trim(), blank(in.description()), toJson(perms), t.orgId(), key).update();
        audit.record("role.update", "role", key, null, null, Map.of("permissions", perms.size()));
        access.invalidateAll();
        return role(key);
    }

    @Transactional
    public void deleteRole(String key) {
        TenantContext.Tenant t = TenantContext.require();
        Role r = role(key);
        if (r.system()) {
            throw ApiException.conflict("system_role", "Standard roles cannot be deleted.");
        }
        if (r.members() > 0) {
            throw ApiException.conflict("role_in_use", "Move its " + r.members() + " member(s) to another role first.");
        }
        jdbc.sql("DELETE FROM roles WHERE org_id = ? AND role_key = ?").params(t.orgId(), key).update();
        audit.record("role.delete", "role", key, null, null, Map.of());
    }

    // ---- facilities --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Facility> facilities() {
        return jdbc.sql("SELECT id, name, mfl_code, keph_level, ownership, county, sub_county, phone, address_line, active FROM facilities WHERE org_id = ? ORDER BY name")
                .param(TenantContext.require().orgId()).query(StaffService::facility).list();
    }

    @Transactional
    public Facility createFacility(FacilityInput in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO facilities (org_id, name, mfl_code, keph_level, ownership, county, sub_county, phone, address_line)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), in.name().trim(), blank(in.mflCode()), in.kephLevel(), in.ownership(), blank(in.county()),
                            blank(in.subCounty()), blank(in.phone()), blank(in.addressLine()))
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("mfl_taken", "A facility with that MFL code already exists.");
        }
        // The creator gets the new site so they can use it at once.
        jdbc.sql("INSERT INTO practitioner_facilities (org_id, practitioner_id, facility_id) VALUES (?, ?, ?)").params(t.orgId(), t.practitionerId(), id).update();
        audit.record("facility.create", "facility", id, id, null, Map.of("name", in.name().trim()));
        access.invalidateAll();
        return facility(id);
    }

    @Transactional
    public Facility updateFacility(UUID id, FacilityUpdate in) {
        TenantContext.Tenant t = TenantContext.require();
        Facility before = facility(id);
        if (!in.active() && before.active() && jdbc.sql("SELECT count(*) FROM facilities WHERE org_id = ? AND active AND id <> ?").params(t.orgId(), id)
                .query(Long.class).single() == 0) {
            throw ApiException.conflict("last_facility", "An organisation needs at least one active facility.");
        }
        try {
            jdbc.sql("""
                    UPDATE facilities SET name = ?, mfl_code = ?, keph_level = ?, ownership = ?, county = ?, sub_county = ?, phone = ?,
                           address_line = ?, active = ? WHERE org_id = ? AND id = ?""")
                    .params(in.name().trim(), blank(in.mflCode()), in.kephLevel(), in.ownership(), blank(in.county()), blank(in.subCounty()),
                            blank(in.phone()), blank(in.addressLine()), in.active(), t.orgId(), id).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("mfl_taken", "A facility with that MFL code already exists.");
        }
        audit.record("facility.update", "facility", id, id, null, Map.of("active", in.active()));
        return facility(id);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Facility facility(UUID id) {
        return jdbc.sql("SELECT id, name, mfl_code, keph_level, ownership, county, sub_county, phone, address_line, active FROM facilities WHERE org_id = ? AND id = ?")
                .params(TenantContext.require().orgId(), id).query(StaffService::facility).optional().orElseThrow(() -> ApiException.notFound("Facility"));
    }

    private static Facility facility(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Facility(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("mfl_code"), (Integer) rs.getObject("keph_level"),
                rs.getString("ownership"), rs.getString("county"), rs.getString("sub_county"), rs.getString("phone"), rs.getString("address_line"),
                rs.getBoolean("active"));
    }

    private Role role(String key) {
        return roles().stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow(() -> ApiException.notFound("Role"));
    }

    private List<String> permissionsOf(String jsonText, String key) {
        if (DefaultRoles.ADMIN.equals(key)) {
            return Permissions.ALL;
        }
        try {
            return json.readValue(jsonText, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(List<String> perms) {
        try {
            return json.writeValueAsString(perms);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> checkPermissions(TenantContext.Tenant t, List<String> requested) {
        Set<String> distinct = new LinkedHashSet<>(requested);
        for (String p : distinct) {
            if (!Permissions.ALL.contains(p)) {
                throw ApiException.badRequest("unknown_permission", "'" + p + "' is not a permission.");
            }
            if (!t.can(p)) {
                throw ApiException.forbidden("You cannot grant '" + p + "' because you do not hold it.");
            }
        }
        return List.copyOf(distinct);
    }

    private boolean canGrant(TenantContext.Tenant t, String roleKey) {
        List<String> perms = jdbc.sql("SELECT permissions::text FROM roles WHERE org_id = ? AND role_key = ?").params(t.orgId(), roleKey)
                .query(String.class).optional().map(s -> permissionsOf(s, roleKey)).orElse(null);
        return perms != null && t.permissions().containsAll(perms);
    }

    private Set<String> checkRoles(TenantContext.Tenant t, List<String> keys) {
        Set<String> distinct = new LinkedHashSet<>(keys);
        for (String key : distinct) {
            Long exists = jdbc.sql("SELECT count(*) FROM roles WHERE org_id = ? AND role_key = ?").params(t.orgId(), key).query(Long.class).single();
            if (exists == 0) {
                throw ApiException.badRequest("unknown_role", "There is no role '" + key + "'.");
            }
            if (!canGrant(t, key)) {
                throw ApiException.forbidden("You cannot assign '" + key + "': it holds permissions you do not have.");
            }
        }
        return distinct;
    }

    private Set<UUID> checkFacilities(TenantContext.Tenant t, List<UUID> ids) {
        Set<UUID> distinct = new LinkedHashSet<>(ids);
        for (UUID id : distinct) {
            if (jdbc.sql("SELECT count(*) FROM facilities WHERE org_id = ? AND id = ?").params(t.orgId(), id).query(Long.class).single() == 0) {
                throw ApiException.badRequest("unknown_facility", "That facility does not exist.");
            }
            if (!t.can(Permissions.FACILITIES_MANAGE) && !t.facilityIds().contains(id)) {
                throw ApiException.forbidden("You can only assign facilities you work at.");
            }
        }
        return distinct;
    }

    private void assign(TenantContext.Tenant t, UUID id, Set<String> roles, Set<UUID> facilities) {
        for (String r : roles) {
            jdbc.sql("INSERT INTO practitioner_roles (org_id, practitioner_id, role_key) VALUES (?, ?, ?)").params(t.orgId(), id, r).update();
        }
        for (UUID f : facilities) {
            jdbc.sql("INSERT INTO practitioner_facilities (org_id, practitioner_id, facility_id) VALUES (?, ?, ?)").params(t.orgId(), id, f).update();
        }
    }

    /** An organisation must never be left with nobody who can administer it. */
    private void requireAnotherAdmin(UUID except) {
        Long others = jdbc.sql("""
                SELECT count(*) FROM practitioner_roles pr JOIN practitioners p ON p.org_id = pr.org_id AND p.id = pr.practitioner_id
                 WHERE pr.org_id = ? AND pr.role_key = ? AND p.status = 'ACTIVE' AND p.id <> ?""")
                .params(TenantContext.require().orgId(), DefaultRoles.ADMIN, except).query(Long.class).single();
        if (others == 0) {
            throw ApiException.conflict("last_admin", "That would leave the organisation without an administrator.");
        }
    }

    private Staff load(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Staff base = jdbc.sql("""
                SELECT id, email, full_name, cadre, licence_body, licence_no, phone, status, must_change_password, created_at
                  FROM practitioners WHERE org_id = ? AND id = ?""").params(t.orgId(), id)
                .query((rs, n) -> new Staff(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("full_name"), rs.getString("cadre"),
                        rs.getString("licence_body"), rs.getString("licence_no"), rs.getString("phone"), rs.getString("status"),
                        rs.getBoolean("must_change_password"), rs.getObject("created_at", OffsetDateTime.class).toInstant(), List.of(), List.of()))
                .optional().orElseThrow(() -> ApiException.notFound("Staff member"));
        List<String> roles = jdbc.sql("SELECT role_key FROM practitioner_roles WHERE practitioner_id = ? ORDER BY role_key").param(id).query(String.class).list();
        List<UUID> facilities = jdbc.sql("SELECT facility_id FROM practitioner_facilities WHERE practitioner_id = ?").param(id).query(UUID.class).list();
        return new Staff(base.id(), base.email(), base.fullName(), base.cadre(), base.licenceBody(), base.licenceNo(), base.phone(), base.status(),
                base.mustChangePassword(), base.createdAt(), roles, facilities);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
