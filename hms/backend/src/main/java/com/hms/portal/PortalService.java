package com.hms.portal;

import static com.hms.portal.PortalModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.security.PortalSession;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a patient sees of their own record. Every query is pinned to the patient in the session (never a patient id from
 * the request) and, by row-level security, to their organisation. Results and reports appear only once a clinician has
 * released them; withdrawing a release hides them again.
 */
@Service
public class PortalService {

    private static final int MAX_OPEN_REQUESTS = 3;
    private static final int MAX_DAYS_AHEAD = 120;

    private final JdbcClient jdbc;
    private final AuditService audit;

    public PortalService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private UUID org() {
        return TenantContext.require().orgId();
    }

    private UUID me() {
        return PortalSession.patientId();
    }

    private void read(String resource) {
        audit.record("portal.read", "patient", me(), null, null, Map.of("resource", resource));
    }

    @Transactional(readOnly = true)
    public Me profile() {
        return jdbc.sql("SELECT given_name, family_name, birth_date, phone, email FROM patients WHERE org_id = ? AND id = ?").params(org(), me())
                .query((rs, n) -> new Me(rs.getString("given_name"), rs.getString("family_name"), rs.getObject("birth_date", LocalDate.class), rs.getString("phone"), rs.getString("email")))
                .optional().orElseThrow(() -> ApiException.notFound("Profile"));
    }

    @Transactional
    public Slice<LabResult> results(String cursor, Integer limit) {
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(org(), me()));
        String sql = """
                SELECT i.id, lt.name, i.result_numeric, i.result_text, lt.unit, i.flag, lt.ref_low, lt.ref_high, i.validated_at, i.released_at
                  FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id JOIN lab_tests lt ON lt.org_id = i.org_id AND lt.id = i.test_id
                 WHERE i.org_id = ? AND o.patient_id = ? AND i.status = 'VALIDATED' AND i.released_at IS NOT NULL""";
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql += " AND (i.released_at, i.id) < (?::timestamptz, ?::uuid)";
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        p.add(size + 1);
        List<LabResult> rows = jdbc.sql(sql + " ORDER BY i.released_at DESC, i.id DESC LIMIT ?").params(p.toArray())
                .query((rs, n) -> new LabResult(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBigDecimal("result_numeric"), rs.getString("result_text"), rs.getString("unit"),
                        rs.getString("flag"), rs.getBigDecimal("ref_low"), rs.getBigDecimal("ref_high"), instant(rs.getObject("validated_at", OffsetDateTime.class)),
                        instant(rs.getObject("released_at", OffsetDateTime.class)))).list();
        read("lab_results");
        boolean more = rows.size() > size;
        List<LabResult> page = more ? rows.subList(0, size) : rows;
        return new Slice<>(page, more ? Slice.encode(Map.of("t", page.get(page.size() - 1).releasedAt().toString(), "i", page.get(page.size() - 1).id().toString())) : null);
    }

    @Transactional
    public List<ImagingReport> imaging() {
        List<ImagingReport> rows = jdbc.sql("""
                SELECT o.id, ip.name, ip.modality, o.findings, o.impression, o.signed_at, o.released_at
                  FROM imaging_orders o JOIN imaging_procedures ip ON ip.org_id = o.org_id AND ip.id = o.procedure_id
                 WHERE o.org_id = ? AND o.patient_id = ? AND o.status = 'SIGNED' AND o.released_at IS NOT NULL ORDER BY o.released_at DESC LIMIT 100""").params(org(), me())
                .query((rs, n) -> new ImagingReport(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("modality"), rs.getString("findings"), rs.getString("impression"),
                        instant(rs.getObject("signed_at", OffsetDateTime.class)), instant(rs.getObject("released_at", OffsetDateTime.class)))).list();
        read("imaging_reports");
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Appointment> appointments() {
        return jdbc.sql("""
                SELECT a.id, f.name AS facility, a.starts_at, a.status, a.reason FROM appointments a JOIN facilities f ON f.org_id = a.org_id AND f.id = a.facility_id
                 WHERE a.org_id = ? AND a.patient_id = ? AND a.starts_at > now() - interval '90 days' ORDER BY a.starts_at DESC LIMIT 50""").params(org(), me())
                .query((rs, n) -> new Appointment(rs.getObject("id", UUID.class), rs.getString("facility"), instant(rs.getObject("starts_at", OffsetDateTime.class)), rs.getString("status"), rs.getString("reason"))).list();
    }

    @Transactional
    public List<Medication> medications() {
        List<Medication> rows = jdbc.sql("""
                SELECT id, drug_name, dose, route, frequency, duration_days, status, created_at FROM orders
                 WHERE org_id = ? AND patient_id = ? AND kind = 'MEDICATION' AND status <> 'CANCELLED' AND created_at > now() - interval '180 days' ORDER BY created_at DESC LIMIT 100""").params(org(), me())
                .query((rs, n) -> new Medication(rs.getObject("id", UUID.class), rs.getString("drug_name"), rs.getString("dose"), rs.getString("route"), rs.getString("frequency"),
                        (Integer) rs.getObject("duration_days"), rs.getString("status"), instant(rs.getObject("created_at", OffsetDateTime.class)))).list();
        read("medications");
        return rows;
    }

    @Transactional
    public List<Allergy> allergies() {
        List<Allergy> rows = jdbc.sql("SELECT id, substance, reaction, severity FROM allergies WHERE org_id = ? AND patient_id = ? AND status = 'ACTIVE' ORDER BY substance LIMIT 100").params(org(), me())
                .query((rs, n) -> new Allergy(rs.getObject("id", UUID.class), rs.getString("substance"), rs.getString("reaction"), rs.getString("severity"))).list();
        read("allergies");
        return rows;
    }

    @Transactional(readOnly = true)
    public List<FacilityRef> facilities() {
        return jdbc.sql("SELECT id, name FROM facilities WHERE org_id = ? ORDER BY name LIMIT 200").param(org()).query((rs, n) -> new FacilityRef(rs.getObject("id", UUID.class), rs.getString("name"))).list();
    }

    // ---- appointment requests ----------------------------------------------------------------

    @Transactional
    public AppointmentRequest request(RequestInput in) {
        LocalDate today = LocalDate.now();
        if (in.preferredDate().isBefore(today) || in.preferredDate().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.badRequest("preferred_date", "Choose a date from today to " + MAX_DAYS_AHEAD + " days ahead.");
        }
        Long facility = jdbc.sql("SELECT count(*) FROM facilities WHERE org_id = ? AND id = ?").params(org(), in.facilityId()).query(Long.class).single();
        if (facility == 0) {
            throw ApiException.notFound("Facility");
        }
        Long open = jdbc.sql("SELECT count(*) FROM appointment_requests WHERE org_id = ? AND patient_id = ? AND status = 'REQUESTED'").params(org(), me()).query(Long.class).single();
        if (open >= MAX_OPEN_REQUESTS) {
            throw ApiException.conflict("too_many_requests", "You already have " + MAX_OPEN_REQUESTS + " requests waiting. Cancel one or wait for the facility to answer.");
        }
        UUID id = jdbc.sql("INSERT INTO appointment_requests (org_id, facility_id, patient_id, preferred_date, reason) VALUES (?, ?, ?, ?, ?) RETURNING id")
                .params(org(), in.facilityId(), me(), in.preferredDate(), in.reason().trim()).query(UUID.class).single();
        audit.record("portal.appointment.request", "patient", me(), in.facilityId(), null, Map.of("request", id.toString()));
        return one(id);
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequest> requests() {
        return jdbc.sql(REQUEST_SQL + " WHERE r.org_id = ? AND r.patient_id = ? ORDER BY r.created_at DESC LIMIT 50").params(org(), me()).query(PortalService::requestMap).list();
    }

    @Transactional
    public AppointmentRequest cancel(UUID id) {
        int n = jdbc.sql("UPDATE appointment_requests SET status = 'CANCELLED' WHERE org_id = ? AND id = ? AND patient_id = ? AND status = 'REQUESTED'").params(org(), id, me()).update();
        if (n == 0) {
            // Either it is not this patient's, or it is no longer waiting: the caller learns only that it cannot be cancelled.
            if (jdbc.sql("SELECT count(*) FROM appointment_requests WHERE org_id = ? AND id = ? AND patient_id = ?").params(org(), id, me()).query(Long.class).single() == 0) {
                throw ApiException.notFound("Request");
            }
            throw ApiException.conflict("not_waiting", "That request has already been answered or cancelled.");
        }
        audit.record("portal.appointment.cancel", "patient", me(), null, null, Map.of("request", id.toString()));
        return one(id);
    }

    static final String REQUEST_SQL = """
            SELECT r.id, r.facility_id, f.name AS facility, p.given_name || ' ' || p.family_name AS patient_name, r.preferred_date, r.reason, r.status, r.response_note, r.created_at
              FROM appointment_requests r JOIN facilities f ON f.org_id = r.org_id AND f.id = r.facility_id JOIN patients p ON p.org_id = r.org_id AND p.id = r.patient_id""";

    static AppointmentRequest requestMap(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new AppointmentRequest(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getString("facility"), rs.getString("patient_name"),
                rs.getObject("preferred_date", LocalDate.class), rs.getString("reason"), rs.getString("status"), rs.getString("response_note"), instant(rs.getObject("created_at", OffsetDateTime.class)));
    }

    private AppointmentRequest one(UUID id) {
        return jdbc.sql(REQUEST_SQL + " WHERE r.org_id = ? AND r.id = ?").params(org(), id).query(PortalService::requestMap).single();
    }

    static Instant instant(OffsetDateTime v) {
        return v == null ? null : v.toInstant();
    }
}
