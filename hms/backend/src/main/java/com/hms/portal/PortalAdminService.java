package com.hms.portal;

import static com.hms.portal.PortalModels.*;

import com.hms.notify.NotificationService;
import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.registry.PatientAccess;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What staff do for the portal: hand out invitations, release results, answer appointment requests. */
@Service
public class PortalAdminService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final long VALID_DAYS = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;
    private final NotificationService notifications;

    public PortalAdminService(JdbcClient jdbc, AuditService audit, PatientAccess patients, NotificationService notifications) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
        this.notifications = notifications;
    }

    /** Issues a one-time code, shown once. Staff give it to the patient in person after checking who they are; earlier unused codes stop working. */
    @Transactional
    public Invitation invite(UUID patientId) {
        TenantContext.Tenant t = TenantContext.require();
        patients.requireAlive(patientId);
        if (jdbc.sql("SELECT count(*) FROM portal_accounts WHERE org_id = ? AND patient_id = ?").params(t.orgId(), patientId).query(Long.class).single() > 0) {
            throw ApiException.conflict("account_exists", "That patient already has a portal account.");
        }
        jdbc.sql("UPDATE portal_invitations SET revoked_at = now() WHERE org_id = ? AND patient_id = ? AND used_at IS NULL AND revoked_at IS NULL").params(t.orgId(), patientId).update();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        Instant expires = Instant.now().plus(VALID_DAYS, ChronoUnit.DAYS);
        jdbc.sql("INSERT INTO portal_invitations (org_id, patient_id, code_hash, created_by, expires_at) VALUES (?, ?, ?, ?, ?)")
                .params(t.orgId(), patientId, PortalAuthService.hashCode(code.toString()), t.practitionerId(), java.sql.Timestamp.from(expires)).update();
        String name = jdbc.sql("SELECT given_name || ' ' || family_name FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), patientId).query(String.class).single();
        audit.record("portal.invite", "patient", patientId, null, null, Map.of());
        String shown = code.substring(0, 5) + "-" + code.substring(5);
        // The patient hears that an invitation exists. The code is never in the message: it goes from staff to the patient in person.
        var who = jdbc.sql("SELECT given_name, (SELECT name FROM organisations WHERE id = ?) AS org FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), t.orgId(), patientId)
                .query((rs, n) -> new String[] {rs.getString("given_name"), rs.getString("org")}).single();
        List<String> notices = notifications.enqueueToPatient(patientId, "PORTAL_INVITED",
                Map.of("givenName", who[0], "organisation", who[1], "expiresOn", expires.atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()));
        return new Invitation(shown, expires, name, notices);
    }

    /** What happened to the messages sent to this patient about the portal, newest first. Recipients are masked. */
    @Transactional(readOnly = true)
    public List<NotificationService.Notice> notifications(UUID patientId) {
        patients.require(patientId);
        return notifications.forPatient(patientId, 20);
    }

    @Transactional(readOnly = true)
    public AccountInfo account(UUID patientId) {
        TenantContext.Tenant t = TenantContext.require();
        patients.require(patientId);
        return jdbc.sql("SELECT status, login, last_login_at FROM portal_accounts WHERE org_id = ? AND patient_id = ?").params(t.orgId(), patientId)
                .query((rs, n) -> new AccountInfo(true, rs.getString("status"), rs.getString("login"), PortalService.instant(rs.getObject("last_login_at", OffsetDateTime.class)))).optional()
                .orElse(new AccountInfo(false, null, null, null));
    }

    @Transactional
    public AccountInfo setEnabled(UUID patientId, boolean enabled) {
        TenantContext.Tenant t = TenantContext.require();
        patients.require(patientId);
        int n = jdbc.sql("UPDATE portal_accounts SET status = ?, failed_logins = 0, locked_until = NULL WHERE org_id = ? AND patient_id = ?").params(enabled ? "ACTIVE" : "DISABLED", t.orgId(), patientId).update();
        if (n == 0) {
            throw ApiException.notFound("Portal account");
        }
        audit.record(enabled ? "portal.account.enable" : "portal.account.disable", "patient", patientId, null, null, Map.of());
        return account(patientId);
    }

    /**
     * For a patient who has forgotten their password: removes the account so a fresh invitation can be issued after staff have
     * checked who they are again. The account holds only a sign-in name and a password; nothing about the patient's record goes.
     */
    @Transactional
    public void reset(UUID patientId) {
        TenantContext.Tenant t = TenantContext.require();
        patients.require(patientId);
        int n = jdbc.sql("DELETE FROM portal_accounts WHERE org_id = ? AND patient_id = ?").params(t.orgId(), patientId).update();
        if (n == 0) {
            throw ApiException.notFound("Portal account");
        }
        jdbc.sql("UPDATE portal_invitations SET revoked_at = now() WHERE org_id = ? AND patient_id = ? AND used_at IS NULL AND revoked_at IS NULL").params(t.orgId(), patientId).update();
        audit.record("portal.account.reset", "patient", patientId, null, null, Map.of());
    }

    // ---- appointment requests ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AppointmentRequest> requests(UUID facilityId, String status) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        String sql = PortalService.REQUEST_SQL + " WHERE r.org_id = ? AND r.facility_id = ?" + (status == null || status.isBlank() ? "" : " AND r.status = ?") + " ORDER BY r.created_at LIMIT 200";
        var q = jdbc.sql(sql);
        return (status == null || status.isBlank() ? q.params(t.orgId(), facilityId) : q.params(t.orgId(), facilityId, status)).query(PortalService::requestMap).list();
    }

    @Transactional
    public AppointmentRequest resolve(UUID id, ResolveInput in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID facility = jdbc.sql("SELECT facility_id FROM appointment_requests WHERE org_id = ? AND id = ?").params(t.orgId(), id).query(UUID.class).optional().orElseThrow(() -> ApiException.notFound("Request"));
        t.requireFacility(facility);
        int n = jdbc.sql("UPDATE appointment_requests SET status = ?, response_note = ?, resolved_by = ?, resolved_at = now() WHERE org_id = ? AND id = ? AND status = 'REQUESTED'")
                .params(in.status(), in.note().trim(), t.practitionerId(), t.orgId(), id).update();
        if (n == 0) {
            throw ApiException.conflict("not_waiting", "That request has already been answered or cancelled.");
        }
        UUID patient = jdbc.sql("SELECT patient_id FROM appointment_requests WHERE org_id = ? AND id = ?").params(t.orgId(), id).query(UUID.class).single();
        audit.record("portal.appointment.resolve", "patient", patient, facility, in.note().trim(), Map.of("request", id.toString(), "status", in.status()));
        return jdbc.sql(PortalService.REQUEST_SQL + " WHERE r.org_id = ? AND r.id = ?").params(t.orgId(), id).query(PortalService::requestMap).single();
    }

    // ---- release -----------------------------------------------------------------------------

    @Transactional
    public void releaseLab(UUID itemId, boolean release) {
        TenantContext.Tenant t = TenantContext.require();
        var row = jdbc.sql("SELECT o.facility_id, o.patient_id, i.status FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id WHERE i.org_id = ? AND i.id = ? FOR UPDATE OF i")
                .params(t.orgId(), itemId).query((rs, n) -> new Object[] {rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("status")}).optional()
                .orElseThrow(() -> ApiException.notFound("Result"));
        t.requireFacility((UUID) row[0]);
        if (release && !"VALIDATED".equals(row[2])) {
            throw ApiException.conflict("not_validated", "Only a validated result can be released to the patient.");
        }
        jdbc.sql("UPDATE lab_order_items SET released_at = ?, released_by = ? WHERE org_id = ? AND id = ?")
                .params(release ? java.sql.Timestamp.from(Instant.now()) : null, release ? t.practitionerId() : null, t.orgId(), itemId).update();
        audit.record(release ? "portal.release" : "portal.withdraw", "patient", row[1], (UUID) row[0], null, Map.of("kind", "lab", "item", itemId.toString()));
    }

    @Transactional
    public void releaseImaging(UUID orderId, boolean release) {
        TenantContext.Tenant t = TenantContext.require();
        var row = jdbc.sql("SELECT facility_id, patient_id, status FROM imaging_orders WHERE org_id = ? AND id = ? FOR UPDATE").params(t.orgId(), orderId)
                .query((rs, n) -> new Object[] {rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("status")}).optional()
                .orElseThrow(() -> ApiException.notFound("Imaging order"));
        t.requireFacility((UUID) row[0]);
        if (release && !"SIGNED".equals(row[2])) {
            throw ApiException.conflict("not_signed", "Only a signed report can be released to the patient.");
        }
        jdbc.sql("UPDATE imaging_orders SET released_at = ?, released_by = ? WHERE org_id = ? AND id = ?")
                .params(release ? java.sql.Timestamp.from(Instant.now()) : null, release ? t.practitionerId() : null, t.orgId(), orderId).update();
        audit.record(release ? "portal.release" : "portal.withdraw", "patient", row[1], (UUID) row[0], null, Map.of("kind", "imaging", "order", orderId.toString()));
    }
}
