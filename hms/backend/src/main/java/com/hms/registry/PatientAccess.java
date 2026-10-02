package com.hms.registry;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The one check every other module makes before touching a patient: the patient exists in this
 * organisation, is not merged away, and, if the record is restricted, the caller may see it.
 */
@Component
public class PatientAccess {

    public record Ref(UUID id, UUID registeredFacilityId, boolean restricted, boolean active, boolean deceased) {}

    private final JdbcClient jdbc;

    public PatientAccess(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Ref require(UUID patientId) {
        TenantContext.Tenant t = TenantContext.require();
        Ref ref = jdbc.sql("SELECT id, registered_facility_id, restricted, merged_into IS NULL AS live, deceased_at IS NOT NULL AS dead FROM patients WHERE org_id = ? AND id = ?")
                .params(t.orgId(), patientId)
                .query((rs, n) -> new Ref(rs.getObject("id", UUID.class), rs.getObject("registered_facility_id", UUID.class),
                        rs.getBoolean("restricted"), rs.getBoolean("live"), rs.getBoolean("dead")))
                .optional().orElseThrow(() -> ApiException.notFound("Patient"));
        if (ref.restricted() && !t.can(Permissions.PATIENTS_RESTRICTED)) {
            throw ApiException.forbidden("This record is restricted.");
        }
        return ref;
    }

    /** As {@link #require} but also refuses a record that was merged into another. */
    public Ref requireLive(UUID patientId) {
        Ref ref = require(patientId);
        if (!ref.active()) {
            throw ApiException.conflict("patient_merged", "That patient record was merged into another; use the surviving record.");
        }
        return ref;
    }
}
