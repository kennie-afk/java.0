package com.hms.imaging;

import static com.hms.imaging.ImagingModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imaging. One order is one procedure. A report is written by one person and signed by another;
 * clinicians see only signed reports; a critical finding stays flagged until a named person
 * acknowledges it; a change to a signed report is an amendment with a reason, kept in the history.
 * Image storage (PACS/DICOM) is not part of this module: the record holds the report, not the pixels.
 */
@Service
public class ImagingService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;

    public ImagingService(JdbcClient jdbc, AuditService audit, PatientAccess patients) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
    }

    // ---- catalogue ---------------------------------------------------------------------------

    @Transactional
    public Procedure createProcedure(ProcedureInput in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID id;
        try {
            id = jdbc.sql("INSERT INTO imaging_procedures (org_id, code, name, modality, body_region, price, active) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id")
                    .params(t.orgId(), in.code(), in.name().trim(), in.modality(), blank(in.bodyRegion()), in.price() == null ? BigDecimal.ZERO : in.price(),
                            in.active() == null || in.active()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("procedure_exists", "A procedure with that code already exists.");
        }
        audit.record("imaging.procedure.create", "imaging_procedure", id, null, null, Map.of("code", in.code()));
        return procedure(id);
    }

    @Transactional
    public Procedure updateProcedure(UUID id, ProcedureInput in) {
        TenantContext.Tenant t = TenantContext.require();
        procedure(id);
        try {
            jdbc.sql("UPDATE imaging_procedures SET code = ?, name = ?, modality = ?, body_region = ?, price = ?, active = ? WHERE org_id = ? AND id = ?")
                    .params(in.code(), in.name().trim(), in.modality(), blank(in.bodyRegion()), in.price() == null ? BigDecimal.ZERO : in.price(),
                            in.active() == null || in.active(), t.orgId(), id).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("procedure_exists", "A procedure with that code already exists.");
        }
        audit.record("imaging.procedure.update", "imaging_procedure", id, null, null, Map.of());
        return procedure(id);
    }

    @Transactional(readOnly = true)
    public List<Procedure> procedures(String q, boolean activeOnly) {
        List<Object> p = new ArrayList<>(List.of(TenantContext.require().orgId()));
        String sql = PROC_SQL + " WHERE org_id = ?" + (activeOnly ? " AND active" : "");
        if (q != null && !q.isBlank()) {
            sql += " AND (lower(name) LIKE ? OR code LIKE ?)";
            p.add("%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            p.add(q.trim().toUpperCase().replace("%", "") + "%");
        }
        return jdbc.sql(sql + " ORDER BY name LIMIT 500").params(p.toArray()).query(ImagingService::procMap).list();
    }

    private static final String PROC_SQL = "SELECT id, code, name, modality, body_region, price, active FROM imaging_procedures";

    private Procedure procedure(UUID id) {
        return jdbc.sql(PROC_SQL + " WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query(ImagingService::procMap).optional()
                .orElseThrow(() -> ApiException.notFound("Procedure"));
    }

    private static Procedure procMap(ResultSet rs, int n) throws SQLException {
        return new Procedure(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("modality"), rs.getString("body_region"),
                rs.getBigDecimal("price"), rs.getBoolean("active"));
    }

    // ---- orders ------------------------------------------------------------------------------

    @Transactional
    public Order order(OrderInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        patients.requireLive(in.patientId());
        if (in.encounterId() != null) {
            Long ok = jdbc.sql("SELECT count(*) FROM encounters WHERE org_id = ? AND id = ? AND patient_id = ? AND facility_id = ? AND status = 'OPEN'")
                    .params(t.orgId(), in.encounterId(), in.patientId(), in.facilityId()).query(Long.class).single();
            if (ok == 0) {
                throw ApiException.badRequest("encounter_invalid", "That is not an open encounter for this patient at this facility.");
            }
        }
        if (!procedure(in.procedureId()).active()) {
            throw ApiException.conflict("procedure_inactive", "That procedure is no longer offered.");
        }
        String number = nextNumber(t, in.facilityId());
        UUID id = jdbc.sql("""
                INSERT INTO imaging_orders (org_id, facility_id, patient_id, encounter_id, procedure_id, order_number, priority, clinical_info, ordered_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(t.orgId(), in.facilityId(), in.patientId(), in.encounterId(), in.procedureId(), number, in.priority() == null ? "ROUTINE" : in.priority(),
                        blank(in.clinicalInfo()), t.practitionerId()).query(UUID.class).single();
        audit.record("imaging.order", "patient", in.patientId(), in.facilityId(), null, Map.of("order", number));
        return load(id);
    }

    @Transactional
    public Order cancel(UUID id, CancelInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Ctx c = lock(id);
        t.requireFacility(c.facilityId);
        if ("CANCELLED".equals(c.status)) {
            throw ApiException.conflict("already_cancelled", "That order is already cancelled.");
        }
        if (!"ORDERED".equals(c.status)) {
            throw ApiException.conflict("already_performed", "A study that has been performed cannot be cancelled.");
        }
        jdbc.sql("UPDATE imaging_orders SET status = 'CANCELLED', cancel_reason = ? WHERE org_id = ? AND id = ?").params(in.reason().trim(), t.orgId(), id).update();
        audit.record("imaging.cancel", "patient", c.patientId, c.facilityId, in.reason().trim(), Map.of("order", c.number));
        return load(id);
    }

    @Transactional
    public Order perform(UUID id, PerformInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Ctx c = lock(id);
        t.requireFacility(c.facilityId);
        if (!"ORDERED".equals(c.status)) {
            throw ApiException.conflict("not_orderable", "Only an order waiting for its study can be marked performed (this one is " + c.status + ").");
        }
        jdbc.sql("UPDATE imaging_orders SET status = 'PERFORMED', performed_by = ?, performed_at = now(), technique_note = ? WHERE org_id = ? AND id = ?")
                .params(t.practitionerId(), in == null ? null : blank(in.techniqueNote()), t.orgId(), id).update();
        audit.record("imaging.perform", "patient", c.patientId, c.facilityId, null, Map.of("order", c.number));
        return load(id);
    }

    @Transactional
    public Order report(UUID id, ReportInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Ctx c = lock(id);
        t.requireFacility(c.facilityId);
        if (!"PERFORMED".equals(c.status)) {
            throw ApiException.conflict("not_performed", "A report is written once, after the study is performed (this order is " + c.status + ").");
        }
        writeReport(t, c, in.findings(), in.impression(), in.critical(), in.criticalNote(), null);
        return load(id);
    }

    /** Changes a signed report. It goes back for a fresh signature by someone other than the amender. */
    @Transactional
    public Order amend(UUID id, AmendInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Ctx c = lock(id);
        t.requireFacility(c.facilityId);
        if (!"SIGNED".equals(c.status)) {
            throw ApiException.conflict("not_signed", "Only a signed report is amended.");
        }
        jdbc.sql("UPDATE imaging_orders SET signed_by = NULL, signed_at = NULL, released_at = NULL, released_by = NULL WHERE org_id = ? AND id = ?").params(t.orgId(), id).update();
        writeReport(t, c, in.findings(), in.impression(), in.critical(), in.criticalNote(), in.reason().trim());
        audit.record("imaging.amend", "patient", c.patientId, c.facilityId, in.reason().trim(), Map.of("order", c.number));
        return load(id);
    }

    private void writeReport(TenantContext.Tenant t, Ctx c, String findings, String impression, boolean critical, String criticalNote, String reason) {
        if (critical && (criticalNote == null || criticalNote.isBlank())) {
            throw ApiException.badRequest("critical_note_required", "A critical finding needs a note saying what it is.");
        }
        int version = c.version + 1;
        String note = critical ? criticalNote.trim() : null;
        jdbc.sql("""
                UPDATE imaging_orders SET status = 'REPORTED', findings = ?, impression = ?, critical = ?, critical_note = ?, reported_by = ?, reported_at = now(),
                       critical_ack_by = NULL, critical_ack_at = NULL, critical_ack_note = NULL, version = ? WHERE org_id = ? AND id = ?""")
                .params(blank(findings), impression.trim(), critical, note, t.practitionerId(), version, t.orgId(), c.id).update();
        jdbc.sql("INSERT INTO imaging_report_history (org_id, order_id, version, findings, impression, critical, reported_by, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(t.orgId(), c.id, version, blank(findings), impression.trim(), critical, t.practitionerId(), reason).update();
        audit.record("imaging.report", "patient", c.patientId, c.facilityId, null, Map.of("order", c.number, "critical", critical));
    }

    @Transactional
    public Order sign(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Ctx c = lock(id);
        t.requireFacility(c.facilityId);
        if (!"REPORTED".equals(c.status)) {
            throw ApiException.conflict("not_reported", "Only a written report awaiting signature can be signed.");
        }
        if (t.practitionerId().equals(c.reportedBy)) {
            throw ApiException.forbidden("A report must be signed by someone other than the person who wrote it.");
        }
        jdbc.sql("UPDATE imaging_orders SET status = 'SIGNED', signed_by = ?, signed_at = now() WHERE org_id = ? AND id = ?").params(t.practitionerId(), t.orgId(), id).update();
        audit.record("imaging.sign", "patient", c.patientId, c.facilityId, null, Map.of("order", c.number));
        return load(id);
    }

    @Transactional
    public Order acknowledge(UUID id, AckInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Ctx c = lock(id);
        t.requireFacility(c.facilityId);
        if (!c.critical) {
            throw ApiException.conflict("not_critical", "That report has no critical finding.");
        }
        if (c.ackAt != null) {
            throw ApiException.conflict("already_acknowledged", "That critical finding has already been acknowledged.");
        }
        jdbc.sql("UPDATE imaging_orders SET critical_ack_by = ?, critical_ack_at = now(), critical_ack_note = ? WHERE org_id = ? AND id = ?")
                .params(t.practitionerId(), in.note().trim(), t.orgId(), id).update();
        audit.record("imaging.critical.ack", "patient", c.patientId, c.facilityId, in.note().trim(), Map.of("order", c.number));
        return load(id);
    }

    /** Critical findings nobody has acknowledged, most recent first. A clinician sees one only once it is signed. */
    @Transactional(readOnly = true)
    public List<CriticalRow> critical(UUID facilityId) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        boolean sees = t.can(Permissions.IMAGING_PERFORM) || t.can(Permissions.IMAGING_SIGN);
        return jdbc.sql("""
                SELECT o.id, o.order_number, o.patient_id, p.given_name || ' ' || p.family_name AS patient_name, ip.name, o.critical_note, o.reported_at
                  FROM imaging_orders o JOIN imaging_procedures ip ON ip.org_id = o.org_id AND ip.id = o.procedure_id
                  JOIN patients p ON p.org_id = o.org_id AND p.id = o.patient_id
                 WHERE o.org_id = ? AND o.facility_id = ? AND o.critical AND o.critical_ack_at IS NULL AND o.status IN ('REPORTED', 'SIGNED') AND (? OR o.status = 'SIGNED')
                 ORDER BY o.reported_at DESC LIMIT 200""").params(t.orgId(), facilityId, sees)
                .query((rs, n) -> new CriticalRow(rs.getObject("id", UUID.class), rs.getString("order_number"), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                        rs.getString("name"), rs.getString("critical_note"), rs.getObject("reported_at", OffsetDateTime.class).toInstant())).list();
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        UUID facility = jdbc.sql("SELECT facility_id FROM imaging_orders WHERE org_id = ? AND id = ?").params(t.orgId(), id).query(UUID.class).optional()
                .orElseThrow(() -> ApiException.notFound("Imaging order"));
        t.requireFacility(facility);
        return jdbc.sql("SELECT version, findings, impression, critical, reported_by, reported_at, reason FROM imaging_report_history WHERE org_id = ? AND order_id = ? ORDER BY version")
                .params(t.orgId(), id).query((rs, n) -> new HistoryEntry(rs.getInt("version"), rs.getString("findings"), rs.getString("impression"), rs.getBoolean("critical"),
                        rs.getObject("reported_by", UUID.class), rs.getObject("reported_at", OffsetDateTime.class).toInstant(), rs.getString("reason"))).list();
    }

    @Transactional(readOnly = true)
    public Slice<OrderRow> list(UUID facilityId, UUID patientId, String status, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(t.orgId()));
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.order_number, o.patient_id, p.given_name || ' ' || p.family_name AS patient_name, ip.name AS procedure_name, ip.modality, o.priority, o.status,
                       o.created_at, (o.critical AND o.critical_ack_at IS NULL) AS crit
                  FROM imaging_orders o JOIN patients p ON p.org_id = o.org_id AND p.id = o.patient_id
                  JOIN imaging_procedures ip ON ip.org_id = o.org_id AND ip.id = o.procedure_id WHERE o.org_id = ?""");
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND o.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND o.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND o.patient_id = ?");
            p.add(patientId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND o.status = ?");
            p.add(status);
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (o.created_at, o.id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY o.created_at DESC, o.id DESC LIMIT ?");
        p.add(size + 1);
        List<OrderRow> rows = jdbc.sql(sql.toString()).params(p.toArray())
                .query((rs, n) -> new OrderRow(rs.getObject("id", UUID.class), rs.getString("order_number"), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                        rs.getString("procedure_name"), rs.getString("modality"), rs.getString("priority"), rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getBoolean("crit"))).list();
        boolean more = rows.size() > size;
        List<OrderRow> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).createdAt().toString(), "i", page.get(page.size() - 1).id().toString())) : null;
        return new Slice<>(page, next);
    }

    @Transactional(readOnly = true)
    public Order open(UUID id) {
        Order o = load(id);
        TenantContext.require().requireFacility(o.facilityId());
        patients.require(o.patientId());
        return o;
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Ctx(UUID id, UUID facilityId, UUID patientId, String number, String status, int version, UUID reportedBy, boolean critical, Instant ackAt) {}

    private Ctx lock(UUID id) {
        return jdbc.sql("SELECT id, facility_id, patient_id, order_number, status, version, reported_by, critical, critical_ack_at FROM imaging_orders WHERE org_id = ? AND id = ? FOR UPDATE")
                .params(TenantContext.require().orgId(), id)
                .query((rs, n) -> new Ctx(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("order_number"),
                        rs.getString("status"), rs.getInt("version"), rs.getObject("reported_by", UUID.class), rs.getBoolean("critical"), instant(rs, "critical_ack_at")))
                .optional().orElseThrow(() -> ApiException.notFound("Imaging order"));
    }

    private String nextNumber(TenantContext.Tenant t, UUID facilityId) {
        jdbc.sql("INSERT INTO facility_counters (org_id, facility_id, name) VALUES (?, ?, 'IMG') ON CONFLICT DO NOTHING").params(t.orgId(), facilityId).update();
        long n = jdbc.sql("UPDATE facility_counters SET next_value = next_value + 1 WHERE facility_id = ? AND name = 'IMG' RETURNING next_value - 1").param(facilityId).query(Long.class).single();
        return String.format("IMG-%06d", n);
    }

    private Order load(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        boolean sees = t.can(Permissions.IMAGING_PERFORM) || t.can(Permissions.IMAGING_SIGN);
        return jdbc.sql("""
                SELECT o.id, o.facility_id, o.patient_id, p.given_name || ' ' || p.family_name AS patient_name, o.encounter_id, o.order_number, o.priority, o.status,
                       o.procedure_id, ip.code, ip.name AS procedure_name, ip.modality, ip.body_region, o.clinical_info, o.ordered_by, o.created_at, o.performed_at,
                       o.technique_note, o.findings, o.impression, o.critical, o.critical_note, o.reported_by, o.reported_at, o.signed_by, o.signed_at,
                       o.critical_ack_at, o.critical_ack_note, o.version, o.released_at
                  FROM imaging_orders o JOIN patients p ON p.org_id = o.org_id AND p.id = o.patient_id
                  JOIN imaging_procedures ip ON ip.org_id = o.org_id AND ip.id = o.procedure_id WHERE o.org_id = ? AND o.id = ?""").params(t.orgId(), id)
                .query((rs, n) -> map(rs, sees)).optional().orElseThrow(() -> ApiException.notFound("Imaging order"));
    }

    /** Clinicians (no perform/sign right) never see a report that is not signed: it may still change. */
    private static Order map(ResultSet rs, boolean sees) throws SQLException {
        String status = rs.getString("status");
        boolean hide = !sees && "REPORTED".equals(status);
        return new Order(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                rs.getObject("encounter_id", UUID.class), rs.getString("order_number"), rs.getString("priority"), status, rs.getObject("procedure_id", UUID.class),
                rs.getString("code"), rs.getString("procedure_name"), rs.getString("modality"), rs.getString("body_region"), rs.getString("clinical_info"),
                rs.getObject("ordered_by", UUID.class), instant(rs, "created_at"), instant(rs, "performed_at"), rs.getString("technique_note"),
                hide ? null : rs.getString("findings"), hide ? null : rs.getString("impression"), !hide && rs.getBoolean("critical"), hide ? null : rs.getString("critical_note"),
                rs.getObject("reported_by", UUID.class), instant(rs, "reported_at"), rs.getObject("signed_by", UUID.class), instant(rs, "signed_at"),
                instant(rs, "critical_ack_at"), rs.getString("critical_ack_note"), rs.getInt("version"), hide, instant(rs, "released_at"));
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
