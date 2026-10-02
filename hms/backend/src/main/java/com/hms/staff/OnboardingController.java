package com.hms.staff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.DefaultRoles;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creates an organisation with its first facility, the standard roles and a first administrator. */
@RestController
@RequestMapping("/v1/organisations")
class OnboardingController {

    record FacilityInput(@NotBlank @Size(min = 2, max = 200) String name,
                         @Size(max = 20) String mflCode,
                         @Min(1) @Max(6) Integer kephLevel,
                         String ownership, String county) {}

    record AdminInput(@NotBlank @Size(min = 2, max = 200) String fullName,
                      @NotBlank @Email String email,
                      @NotBlank @Size(min = 12, max = 100, message = "must be at least 12 characters") String password) {}

    record Request(@NotBlank @Size(min = 2, max = 200) String organisationName,
                   @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}$", message = "lowercase letters, digits and hyphens") String slug,
                   @Valid FacilityInput facility, @Valid AdminInput admin) {}

    record Response(UUID organisationId, UUID facilityId, UUID practitionerId) {}

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final ObjectMapper json;
    private final AuditService audit;

    OnboardingController(JdbcClient jdbc, PasswordEncoder encoder, ObjectMapper json, AuditService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.json = json;
        this.audit = audit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Response onboard(@Valid @RequestBody Request in) throws Exception {
        // Checked before anything is created, through the owner-rights lookup (no tenant exists yet).
        if (jdbc.sql("SELECT count(*) FROM login_lookup(?::citext)").param(in.admin().email().trim()).query(Long.class).single() > 0) {
            throw ApiException.conflict("email_taken", "That email address is already registered.");
        }
        UUID orgId;
        try {
            orgId = jdbc.sql("SELECT create_organisation(?, ?::citext)").params(in.organisationName().trim(), in.slug()).query(UUID.class).single();
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiException.conflict("slug_taken", "That organisation address is already taken.");
        }
        // The organisation did not exist when the transaction began, so scope it to the new tenant now:
        // from here every statement is under the same row-level-security rules as any other request.
        jdbc.sql("SELECT set_config('app.org_id', ?, true)").param(orgId.toString()).query().singleRow();

        UUID facilityId = jdbc.sql("""
                INSERT INTO facilities (org_id, name, mfl_code, keph_level, ownership, county)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(orgId, in.facility().name().trim(), in.facility().mflCode(), in.facility().kephLevel(),
                        in.facility().ownership(), in.facility().county())
                .query(UUID.class).single();

        for (DefaultRoles.Template t : DefaultRoles.ALL_TEMPLATES) {
            jdbc.sql("INSERT INTO roles (org_id, role_key, label, description, is_system, permissions) VALUES (?, ?, ?, ?, true, ?::jsonb)")
                    .params(orgId, t.key(), t.label(), t.description(), json.writeValueAsString(t.permissions())).update();
        }

        UUID adminId = jdbc.sql("""
                INSERT INTO practitioners (org_id, email, password_hash, full_name, cadre)
                VALUES (?, ?::citext, ?, ?, 'ADMINISTRATIVE') RETURNING id""")
                .params(orgId, in.admin().email().trim(), encoder.encode(in.admin().password()), in.admin().fullName().trim())
                .query(UUID.class).single();
        jdbc.sql("INSERT INTO practitioner_roles (org_id, practitioner_id, role_key) VALUES (?, ?, ?)")
                .params(orgId, adminId, DefaultRoles.ADMIN).update();
        jdbc.sql("INSERT INTO practitioner_facilities (org_id, practitioner_id, facility_id) VALUES (?, ?, ?)")
                .params(orgId, adminId, facilityId).update();

        // The audit service reads the tenant from the thread, so bind it for this last step.
        TenantContext.set(new TenantContext.Tenant(orgId, adminId, Set.of(facilityId), Set.of()));
        try {
            audit.record("organisation.create", "organisation", orgId, null, null, Map.of("slug", in.slug()));
        } finally {
            TenantContext.clear();
        }
        return new Response(orgId, facilityId, adminId);
    }
}
