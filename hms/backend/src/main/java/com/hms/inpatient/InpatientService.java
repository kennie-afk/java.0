package com.hms.inpatient;

import static com.hms.inpatient.InpatientModels.*;

import com.hms.clinical.ClinicalModels;
import com.hms.clinical.ClinicalService;
import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import com.hms.registry.PatientService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wards, beds and admissions. A bed holds one patient and a patient holds one bed: both are database
 * constraints. Admitting opens the inpatient encounter; discharging closes it (needing a primary
 * diagnosis), frees the bed for cleaning, and records a death on the patient record when that is the outcome.
 */
@Service
public class InpatientService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;
    private final PatientService patientService;
    private final ClinicalService clinical;

    public InpatientService(JdbcClient jdbc, AuditService audit, PatientAccess patients, PatientService patientService, ClinicalService clinical) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
        this.patientService = patientService;
        this.clinical = clinical;
    }

    // ---- wards and beds ----------------------------------------------------------------------

    @Transactional
    public Ward createWard(WardInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        UUID id;
        try {
            id = jdbc.sql("INSERT INTO wards (org_id, facility_id, name, kind) VALUES (?, ?, ?, ?) RETURNING id")
                    .params(t.orgId(), in.facilityId(), in.name().trim(), in.kind() == null ? "GENERAL" : in.kind()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("ward_exists", "That facility already has a ward with that name.");
        }
        addBedRows(t, in.facilityId(), id, in.bedLabels());
        audit.record("ward.create", "ward", id, in.facilityId(), null, Map.of("name", in.name().trim(), "beds", in.bedLabels().size()));
        return ward(id);
    }

    @Transactional
    public Ward addBeds(UUID wardId, BedsInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Ward w = ward(wardId);
        t.requireFacility(w.facilityId());
        addBedRows(t, w.facilityId(), wardId, in.labels());
        audit.record("ward.beds.add", "ward", wardId, w.facilityId(), null, Map.of("beds", in.labels().size()));
        return ward(wardId);
    }

    private void addBedRows(TenantContext.Tenant t, UUID facilityId, UUID wardId, List<String> labels) {
        for (String label : new LinkedHashSet<>(labels.stream().map(String::trim).toList())) {
            try {
                jdbc.sql("INSERT INTO beds (org_id, facility_id, ward_id, label) VALUES (?, ?, ?, ?)").params(t.orgId(), facilityId, wardId, label).update();
            } catch (DuplicateKeyException e) {
                throw ApiException.conflict("bed_exists", "Bed '" + label + "' already exists in that ward.");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Ward> wards(UUID facilityId) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        return jdbc.sql(WARD_SQL + " WHERE w.org_id = ? AND w.facility_id = ? GROUP BY w.id ORDER BY w.name").params(t.orgId(), facilityId).query(InpatientService::wardMap).list();
    }

    private Ward ward(UUID id) {
        return jdbc.sql(WARD_SQL + " WHERE w.org_id = ? AND w.id = ? GROUP BY w.id").params(TenantContext.require().orgId(), id).query(InpatientService::wardMap).optional()
                .orElseThrow(() -> ApiException.notFound("Ward"));
    }

    private static final String WARD_SQL = """
            SELECT w.id, w.facility_id, w.name, w.kind, w.active, count(b.id) AS beds,
                   count(b.id) FILTER (WHERE b.status = 'OCCUPIED') AS occupied, count(b.id) FILTER (WHERE b.status = 'AVAILABLE') AS available,
                   count(b.id) FILTER (WHERE b.status = 'CLEANING') AS cleaning, count(b.id) FILTER (WHERE b.status = 'OUT_OF_SERVICE') AS oos
              FROM wards w LEFT JOIN beds b ON b.org_id = w.org_id AND b.ward_id = w.id""";

    private static Ward wardMap(ResultSet rs, int n) throws SQLException {
        return new Ward(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getString("name"), rs.getString("kind"), rs.getBoolean("active"),
                rs.getInt("beds"), rs.getInt("occupied"), rs.getInt("available"), rs.getInt("cleaning"), rs.getInt("oos"));
    }

    @Transactional(readOnly = true)
    public List<Bed> beds(UUID wardId) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(ward(wardId).facilityId());
        return jdbc.sql("""
                SELECT b.id, b.ward_id, w.name AS ward_name, b.label, b.status, a.id AS admission_id, pt.given_name || ' ' || pt.family_name AS patient_name
                  FROM beds b JOIN wards w ON w.org_id = b.org_id AND w.id = b.ward_id
                  LEFT JOIN bed_assignments ba ON ba.org_id = b.org_id AND ba.bed_id = b.id AND ba.released_at IS NULL
                  LEFT JOIN admissions a ON a.org_id = ba.org_id AND a.id = ba.admission_id LEFT JOIN patients pt ON pt.org_id = a.org_id AND pt.id = a.patient_id
                 WHERE b.org_id = ? AND b.ward_id = ? ORDER BY b.label""").params(t.orgId(), wardId)
                .query((rs, n) -> new Bed(rs.getObject("id", UUID.class), rs.getObject("ward_id", UUID.class), rs.getString("ward_name"), rs.getString("label"), rs.getString("status"),
                        rs.getObject("admission_id", UUID.class), rs.getString("patient_name"))).list();
    }

    @Transactional
    public void setBedStatus(UUID bedId, BedStatus in) {
        TenantContext.Tenant t = TenantContext.require();
        var bed = lockBed(bedId);
        t.requireFacility(bed.facilityId);
        if ("OCCUPIED".equals(bed.status)) {
            throw ApiException.conflict("bed_occupied", "An occupied bed is freed by discharging or transferring its patient.");
        }
        jdbc.sql("UPDATE beds SET status = ? WHERE org_id = ? AND id = ?").params(in.status(), t.orgId(), bedId).update();
        audit.record("bed.status", "bed", bedId, bed.facilityId, null, Map.of("status", in.status()));
    }

    // ---- admissions --------------------------------------------------------------------------

    @Transactional
    public Admission admit(AdmitInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        patients.requireAlive(in.patientId());
        BedRow bed = lockBed(in.bedId());
        if (!bed.facilityId.equals(in.facilityId())) {
            throw ApiException.badRequest("bed_elsewhere", "That bed is at another facility.");
        }
        if (!"AVAILABLE".equals(bed.status)) {
            throw ApiException.conflict("bed_unavailable", "That bed is " + bed.status + ".");
        }
        var encounter = clinical.open(new ClinicalModels.OpenEncounter(in.facilityId(), in.patientId(), "IPD", null, in.admittingDiagnosis()));
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO admissions (org_id, facility_id, patient_id, encounter_id, admission_number, admitting_diagnosis, admitted_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), in.facilityId(), in.patientId(), encounter.id(), number(t, in.facilityId()), blank(in.admittingDiagnosis()), t.practitionerId())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("already_admitted", "That patient is already admitted.");
        }
        occupy(t, id, in.bedId(), null);
        audit.record("admission.admit", "admission", id, in.facilityId(), null, Map.of("patient", in.patientId().toString(), "bed", bed.label));
        return load(id);
    }

    @Transactional
    public Admission transfer(UUID id, TransferInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Admission a = lockAdmission(id);
        BedRow target = lockBed(in.toBedId());
        if (!target.facilityId.equals(a.facilityId())) {
            throw ApiException.badRequest("bed_elsewhere", "That bed is at another facility.");
        }
        if (!"AVAILABLE".equals(target.status)) {
            throw ApiException.conflict("bed_unavailable", "That bed is " + target.status + ".");
        }
        UUID fromBed = release(t, id, "CLEANING");
        if (fromBed.equals(in.toBedId())) {
            throw ApiException.badRequest("same_bed", "The patient is already in that bed.");
        }
        occupy(t, id, in.toBedId(), in.reason().trim());
        audit.record("admission.transfer", "admission", id, a.facilityId(), in.reason().trim(), Map.of("to", target.label));
        return load(id);
    }

    @Transactional
    public Admission discharge(UUID id, DischargeInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Admission a = lockAdmission(id);
        // Closing the inpatient encounter enforces the primary-diagnosis rule unless a reason is given.
        String why = blank(in.noDiagnosisReason());
        clinical.close(a.encounterId(), new ClinicalModels.CloseInput(why));
        release(t, id, "CLEANING");
        jdbc.sql("""
                UPDATE admissions SET status = 'DISCHARGED', discharge_type = ?, discharge_summary = ?, discharged_at = now(), discharged_by = ?, version = version + 1
                 WHERE org_id = ? AND id = ?""").params(in.type(), in.summary().trim(), t.practitionerId(), t.orgId(), id).update();
        if ("DIED".equals(in.type())) {
            patientService.markDeceased(a.patientId(), Instant.now(), "Died during admission " + a.admissionNumber());
        }
        audit.record("admission.discharge", "admission", id, a.facilityId(), null, Map.of("type", in.type()));
        return load(id);
    }

    @Transactional(readOnly = true)
    public Admission open(UUID id) {
        Admission a = load(id);
        TenantContext.require().requireFacility(a.facilityId());
        patients.require(a.patientId());
        return a;
    }

    @Transactional(readOnly = true)
    public Slice<Admission> list(UUID facilityId, String status, UUID patientId, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(t.orgId()));
        StringBuilder sql = new StringBuilder(ADMISSION_SQL + " WHERE a.org_id = ?");
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND a.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND a.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            p.add(status);
        }
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND a.patient_id = ?");
            p.add(patientId);
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (a.admitted_at, a.id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY a.admitted_at DESC, a.id DESC LIMIT ?");
        p.add(size + 1);
        List<Admission> rows = jdbc.sql(sql.toString()).params(p.toArray()).query(InpatientService::admission).list();
        boolean more = rows.size() > size;
        List<Admission> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).admittedAt().toString(), "i", page.get(page.size() - 1).id().toString())) : null;
        return new Slice<>(page, next);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record BedRow(UUID facilityId, String status, String label) {}

    private BedRow lockBed(UUID bedId) {
        return jdbc.sql("SELECT facility_id, status, label FROM beds WHERE org_id = ? AND id = ? FOR UPDATE").params(TenantContext.require().orgId(), bedId)
                .query((rs, n) -> new BedRow(rs.getObject("facility_id", UUID.class), rs.getString("status"), rs.getString("label"))).optional()
                .orElseThrow(() -> ApiException.notFound("Bed"));
    }

    private Admission lockAdmission(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        jdbc.sql("SELECT id FROM admissions WHERE org_id = ? AND id = ? FOR UPDATE").params(t.orgId(), id).query(UUID.class).optional().orElseThrow(() -> ApiException.notFound("Admission"));
        Admission a = load(id);
        t.requireFacility(a.facilityId());
        patients.require(a.patientId());
        if (!"ADMITTED".equals(a.status())) {
            throw ApiException.conflict("not_admitted", "That patient has already been discharged.");
        }
        return a;
    }

    private void occupy(TenantContext.Tenant t, UUID admissionId, UUID bedId, String reason) {
        try {
            jdbc.sql("INSERT INTO bed_assignments (org_id, admission_id, bed_id, reason, assigned_by) VALUES (?, ?, ?, ?, ?)")
                    .params(t.orgId(), admissionId, bedId, reason, t.practitionerId()).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("bed_unavailable", "That bed has just been taken.");
        }
        jdbc.sql("UPDATE beds SET status = 'OCCUPIED' WHERE org_id = ? AND id = ?").params(t.orgId(), bedId).update();
    }

    /** Ends the current bed assignment and sets the bed to the given state; returns the bed freed. */
    private UUID release(TenantContext.Tenant t, UUID admissionId, String bedStatus) {
        UUID bed = jdbc.sql("UPDATE bed_assignments SET released_at = now() WHERE org_id = ? AND admission_id = ? AND released_at IS NULL RETURNING bed_id")
                .params(t.orgId(), admissionId).query(UUID.class).single();
        jdbc.sql("UPDATE beds SET status = ? WHERE org_id = ? AND id = ?").params(bedStatus, t.orgId(), bed).update();
        return bed;
    }

    private String number(TenantContext.Tenant t, UUID facilityId) {
        jdbc.sql("INSERT INTO facility_counters (org_id, facility_id, name) VALUES (?, ?, 'ADM') ON CONFLICT DO NOTHING").params(t.orgId(), facilityId).update();
        long n = jdbc.sql("UPDATE facility_counters SET next_value = next_value + 1 WHERE facility_id = ? AND name = 'ADM' RETURNING next_value - 1").param(facilityId).query(Long.class).single();
        return String.format("ADM-%06d", n);
    }

    private static final String ADMISSION_SQL = """
            SELECT a.id, a.facility_id, a.patient_id, pt.given_name || ' ' || pt.family_name AS patient_name, a.encounter_id, a.admission_number, a.status, a.admitting_diagnosis,
                   a.admitted_at, a.discharge_type, a.discharge_summary, a.discharged_at, a.version, b.label AS bed_label, w.name AS ward_name
              FROM admissions a JOIN patients pt ON pt.org_id = a.org_id AND pt.id = a.patient_id
              LEFT JOIN bed_assignments ba ON ba.org_id = a.org_id AND ba.admission_id = a.id AND ba.released_at IS NULL
              LEFT JOIN beds b ON b.org_id = ba.org_id AND b.id = ba.bed_id LEFT JOIN wards w ON w.org_id = b.org_id AND w.id = b.ward_id""";

    private static Admission admission(ResultSet rs, int n) throws SQLException {
        Instant admitted = rs.getObject("admitted_at", OffsetDateTime.class).toInstant();
        OffsetDateTime discharged = rs.getObject("discharged_at", OffsetDateTime.class);
        Instant end = discharged == null ? Instant.now() : discharged.toInstant();
        return new Admission(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                rs.getObject("encounter_id", UUID.class), rs.getString("admission_number"), rs.getString("status"), rs.getString("admitting_diagnosis"), admitted,
                rs.getString("discharge_type"), rs.getString("discharge_summary"), discharged == null ? null : discharged.toInstant(), rs.getString("bed_label"), rs.getString("ward_name"),
                Duration.between(admitted, end).toDays(), rs.getInt("version"), List.of());
    }

    private Admission load(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Admission a = jdbc.sql(ADMISSION_SQL + " WHERE a.org_id = ? AND a.id = ?").params(t.orgId(), id).query(InpatientService::admission).optional()
                .orElseThrow(() -> ApiException.notFound("Admission"));
        List<Assignment> history = jdbc.sql("""
                SELECT ba.bed_id, b.label, w.name AS ward_name, ba.assigned_at, ba.released_at, ba.reason FROM bed_assignments ba
                  JOIN beds b ON b.org_id = ba.org_id AND b.id = ba.bed_id JOIN wards w ON w.org_id = b.org_id AND w.id = b.ward_id
                 WHERE ba.org_id = ? AND ba.admission_id = ? ORDER BY ba.assigned_at""").params(t.orgId(), id)
                .query((rs, n) -> new Assignment(rs.getObject("bed_id", UUID.class), rs.getString("label"), rs.getString("ward_name"), rs.getObject("assigned_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("released_at", OffsetDateTime.class) == null ? null : rs.getObject("released_at", OffsetDateTime.class).toInstant(), rs.getString("reason"))).list();
        return new Admission(a.id(), a.facilityId(), a.patientId(), a.patientName(), a.encounterId(), a.admissionNumber(), a.status(), a.admittingDiagnosis(), a.admittedAt(),
                a.dischargeType(), a.dischargeSummary(), a.dischargedAt(), a.currentBed(), a.currentWard(), a.lengthOfStayDays(), a.version(), history);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
