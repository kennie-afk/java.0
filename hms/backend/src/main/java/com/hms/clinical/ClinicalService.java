package com.hms.clinical;

import static com.hms.clinical.ClinicalModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import com.hms.scheduling.SchedulingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The clinical record. Vitals and notes are append-only (a mistake is retracted or amended, and the
 * original stays); every read of a chart is audited; a closed encounter takes no new facts; a drug
 * that matches a recorded allergy is stopped until a clinician says why they are prescribing it.
 */
@Service
public class ClinicalService {

    /** Cadres who may prescribe. Nursing cadres record care but do not issue medication orders here. */
    static final Set<String> PRESCRIBERS = Set.of("DOCTOR", "CLINICAL_OFFICER", "DENTIST");

    private static final String ENC = "SELECT id, facility_id, patient_id, encounter_type, status, attending_id, appointment_id, chief_complaint, triage_category, triaged_at, started_at, ended_at, version FROM encounters";

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;
    private final SchedulingService scheduling;

    public ClinicalService(JdbcClient jdbc, AuditService audit, PatientAccess patients, SchedulingService scheduling) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
        this.scheduling = scheduling;
    }

    // ---- encounters --------------------------------------------------------------------------

    @Transactional
    public Encounter open(OpenEncounter in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        patients.requireLive(in.patientId());
        if (in.appointmentId() != null) {
            var appt = scheduling.open(in.appointmentId());
            if (!appt.patientId().equals(in.patientId()) || !appt.facilityId().equals(in.facilityId())) {
                throw ApiException.badRequest("appointment_mismatch", "That appointment is for a different patient or facility.");
            }
        }
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO encounters (org_id, facility_id, patient_id, encounter_type, attending_id, appointment_id, chief_complaint)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), in.facilityId(), in.patientId(), in.type(), t.practitionerId(), in.appointmentId(), blank(in.chiefComplaint()))
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("encounter_open", "This patient already has an open " + in.type() + " encounter here.");
        }
        if (in.appointmentId() != null && "CHECKED_IN".equals(scheduling.open(in.appointmentId()).status())) {
            scheduling.transition(in.appointmentId(), "IN_PROGRESS", null);
        }
        audit.record("encounter.open", "encounter", id, in.facilityId(), null, Map.of("type", in.type(), "patient", in.patientId().toString()));
        return encounter(id);
    }

    @Transactional
    public Encounter triage(UUID id, Triage in) {
        TenantContext.Tenant t = TenantContext.require();
        Encounter e = requireOpen(id);
        jdbc.sql("UPDATE encounters SET triage_category = ?, chief_complaint = ?, triaged_by = ?, triaged_at = now(), version = version + 1 WHERE org_id = ? AND id = ?")
                .params(in.category(), in.chiefComplaint().trim(), t.practitionerId(), t.orgId(), id).update();
        if (e.appointmentId() != null) {
            var appt = scheduling.open(e.appointmentId());
            if (Set.of("BOOKED", "CHECKED_IN", "IN_PROGRESS").contains(appt.status())) {
                scheduling.setPriority(e.appointmentId(), in.category());
            }
        }
        audit.record("encounter.triage", "encounter", id, e.facilityId(), null, Map.of("category", in.category()));
        return encounter(id);
    }

    @Transactional
    public Encounter close(UUID id, CloseInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Encounter e = requireOpen(id);
        long primary = jdbc.sql("SELECT count(*) FROM diagnoses WHERE org_id = ? AND encounter_id = ? AND kind = 'PRIMARY' AND certainty <> 'RULED_OUT'")
                .params(t.orgId(), id).query(Long.class).single();
        String why = in == null ? null : blank(in.noDiagnosisReason());
        if (primary == 0 && why == null) {
            throw ApiException.conflict("diagnosis_required", "Record a primary diagnosis before closing, or state why there is none.");
        }
        jdbc.sql("UPDATE encounters SET status = 'CLOSED', ended_at = now(), closed_by = ?, version = version + 1 WHERE org_id = ? AND id = ?")
                .params(t.practitionerId(), t.orgId(), id).update();
        if (e.appointmentId() != null && "IN_PROGRESS".equals(scheduling.open(e.appointmentId()).status())) {
            scheduling.transition(e.appointmentId(), "COMPLETED", null);
        }
        audit.record("encounter.close", "encounter", id, e.facilityId(), why, Map.of("diagnosed", primary > 0));
        return encounter(id);
    }

    @Transactional(readOnly = true)
    public Slice<Encounter> list(UUID patientId, UUID facilityId, String status, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>();
        StringBuilder sql = new StringBuilder(ENC + " WHERE org_id = ? AND facility_id = ANY (?)");
        p.add(t.orgId());
        p.add(t.facilityIds().toArray(UUID[]::new));
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND patient_id = ?");
            p.add(patientId);
        }
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND facility_id = ?");
            p.add(facilityId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            p.add(status);
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (started_at, id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY started_at DESC, id DESC LIMIT ?");
        p.add(size + 1);
        List<Encounter> rows = jdbc.sql(sql.toString()).params(p.toArray()).query(ClinicalService::encounter).list();
        boolean more = rows.size() > size;
        List<Encounter> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).startedAt().toString(), "i", page.get(page.size() - 1).id().toString())) : null;
        return new Slice<>(page, next);
    }

    /** Opens a chart. The read is audited: who looked at whose record is itself clinical-governance data. */
    @Transactional
    public EncounterDetail detail(UUID id) {
        Encounter e = encounter(id);
        TenantContext.require().requireFacility(e.facilityId());
        patients.require(e.patientId());
        audit.record("encounter.read", "encounter", id, e.facilityId(), null, Map.of());
        return new EncounterDetail(e, vitals(id), notes(id), diagnoses(id), orders(id), allergies(e.patientId()));
    }

    // ---- vitals ------------------------------------------------------------------------------

    @Transactional
    public Vitals addVitals(UUID encounterId, VitalsInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Encounter e = requireOpen(encounterId);
        if (in.systolic() != null && in.diastolic() != null && in.diastolic() >= in.systolic()) {
            throw ApiException.badRequest("bp_implausible", "Diastolic pressure must be lower than systolic.");
        }
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO vitals (org_id, encounter_id, patient_id, facility_id, recorded_by, temp_c, pulse, resp_rate, systolic, diastolic, spo2,
                                        weight_kg, height_cm, muac_cm, glucose_mmol, pain_score)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), encounterId, e.patientId(), e.facilityId(), t.practitionerId(), in.tempC(), in.pulse(), in.respRate(), in.systolic(),
                            in.diastolic(), in.spo2(), in.weightKg(), in.heightCm(), in.muacCm(), in.glucoseMmol(), in.painScore())
                    .query(UUID.class).single();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw ApiException.badRequest("vitals_invalid", "Record at least one measurement, and blood pressure needs both values.");
        }
        Vitals v = vitalsRow(id);
        audit.record("vitals.record", "encounter", encounterId, e.facilityId(), null, Map.of("alerts", v.alerts().size()));
        return v;
    }

    @Transactional
    public Vitals retractVitals(UUID vitalsId, Retract in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID encounterId = jdbc.sql("SELECT encounter_id FROM vitals WHERE org_id = ? AND id = ?").params(t.orgId(), vitalsId).query(UUID.class)
                .optional().orElseThrow(() -> ApiException.notFound("Vitals"));
        Encounter e = encounter(encounterId);
        t.requireFacility(e.facilityId());
        try {
            jdbc.sql("INSERT INTO clinical_retractions (org_id, entity_type, entity_id, reason, retracted_by) VALUES (?, 'vitals', ?, ?, ?)")
                    .params(t.orgId(), vitalsId, in.reason().trim(), t.practitionerId()).update();
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("already_retracted", "Those readings were already marked as an error.");
        }
        audit.record("vitals.retract", "encounter", encounterId, e.facilityId(), in.reason().trim(), Map.of("vitals", vitalsId.toString()));
        return vitalsRow(vitalsId);
    }

    private List<Vitals> vitals(UUID encounterId) {
        return jdbc.sql(VITALS_SQL + " WHERE v.org_id = ? AND v.encounter_id = ? ORDER BY v.recorded_at DESC, v.id").params(TenantContext.require().orgId(), encounterId)
                .query(this::vitalsMap).list();
    }

    private Vitals vitalsRow(UUID id) {
        return jdbc.sql(VITALS_SQL + " WHERE v.org_id = ? AND v.id = ?").params(TenantContext.require().orgId(), id).query(this::vitalsMap).single();
    }

    private static final String VITALS_SQL = """
            SELECT v.*, r.reason AS retract_reason, p.birth_date FROM vitals v JOIN patients p ON p.org_id = v.org_id AND p.id = v.patient_id
              LEFT JOIN clinical_retractions r ON r.org_id = v.org_id AND r.entity_type = 'vitals' AND r.entity_id = v.id""";

    private Vitals vitalsMap(ResultSet rs, int n) throws SQLException {
        BigDecimal w = rs.getBigDecimal("weight_kg");
        BigDecimal h = rs.getBigDecimal("height_cm");
        BigDecimal bmi = w != null && h != null ? w.divide(h.movePointLeft(2).pow(2), 1, RoundingMode.HALF_UP) : null;
        int ageYears = java.time.Period.between(rs.getObject("birth_date", java.time.LocalDate.class), java.time.LocalDate.now()).getYears();
        List<String> alerts = alerts(rs, ageYears);
        return new Vitals(rs.getObject("id", UUID.class), rs.getObject("recorded_at", OffsetDateTime.class).toInstant(), rs.getObject("recorded_by", UUID.class),
                rs.getBigDecimal("temp_c"), (Integer) rs.getObject("pulse"), (Integer) rs.getObject("resp_rate"), (Integer) rs.getObject("systolic"),
                (Integer) rs.getObject("diastolic"), (Integer) rs.getObject("spo2"), w, h, rs.getBigDecimal("muac_cm"), rs.getBigDecimal("glucose_mmol"),
                (Integer) rs.getObject("pain_score"), bmi, rs.getString("retract_reason") != null, rs.getString("retract_reason"), alerts);
    }

    /**
     * Screening flags, not diagnoses. Only the thresholds that hold across ages are applied to children;
     * the adult thresholds are applied from 12 years. These are prompts to look, never a clinical decision.
     */
    private List<String> alerts(ResultSet rs, int ageYears) throws SQLException {
        List<String> a = new ArrayList<>();
        Integer spo2 = (Integer) rs.getObject("spo2");
        BigDecimal temp = rs.getBigDecimal("temp_c");
        BigDecimal glucose = rs.getBigDecimal("glucose_mmol");
        if (spo2 != null && spo2 < 90) {
            a.add("LOW_OXYGEN_SATURATION");
        }
        if (temp != null && temp.compareTo(new BigDecimal("38.0")) >= 0) {
            a.add("FEVER");
        }
        if (temp != null && temp.compareTo(new BigDecimal("35.0")) < 0) {
            a.add("LOW_TEMPERATURE");
        }
        if (glucose != null && glucose.compareTo(new BigDecimal("3.0")) < 0) {
            a.add("LOW_GLUCOSE");
        }
        if (ageYears >= 12) {
            Integer sys = (Integer) rs.getObject("systolic");
            Integer pulse = (Integer) rs.getObject("pulse");
            Integer rr = (Integer) rs.getObject("resp_rate");
            if (sys != null && sys < 90) {
                a.add("LOW_BLOOD_PRESSURE");
            }
            if (sys != null && sys >= 180) {
                a.add("SEVERE_HYPERTENSION");
            }
            if (pulse != null && (pulse > 130 || pulse < 40)) {
                a.add("ABNORMAL_PULSE");
            }
            if (rr != null && (rr > 30 || rr < 8)) {
                a.add("ABNORMAL_RESPIRATORY_RATE");
            }
        }
        return a;
    }

    // ---- notes -------------------------------------------------------------------------------

    @Transactional
    public Note addNote(UUID encounterId, NoteInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Encounter e = requireOpen(encounterId);
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO clinical_notes (id, org_id, encounter_id, patient_id, facility_id, thread_id, version, kind, body, author_id)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?)""")
                .params(id, t.orgId(), encounterId, e.patientId(), e.facilityId(), id, in.kind(), in.body().trim(), t.practitionerId()).update();
        audit.record("note.create", "encounter", encounterId, e.facilityId(), null, Map.of("note", id.toString(), "kind", in.kind()));
        return note(id);
    }

    /** A correction is a new version carrying a reason; the earlier text stays on record. */
    @Transactional
    public Note amendNote(UUID threadId, Amend in) {
        TenantContext.Tenant t = TenantContext.require();
        Note latest = jdbc.sql(NOTE_SQL + " WHERE org_id = ? AND thread_id = ? ORDER BY version DESC LIMIT 1").params(t.orgId(), threadId)
                .query(ClinicalService::noteMap).optional().orElseThrow(() -> ApiException.notFound("Note"));
        Encounter e = encounter(jdbc.sql("SELECT encounter_id FROM clinical_notes WHERE id = ?").param(latest.id()).query(UUID.class).single());
        t.requireFacility(e.facilityId());
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("""
                    INSERT INTO clinical_notes (id, org_id, encounter_id, patient_id, facility_id, thread_id, version, kind, body, author_id, amend_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(id, t.orgId(), e.id(), e.patientId(), e.facilityId(), threadId, latest.version() + 1, latest.kind(), in.body().trim(), t.practitionerId(),
                            in.reason().trim()).update();
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("stale_version", "Someone amended this note at the same moment. Reload it.");
        }
        audit.record("note.amend", "encounter", e.id(), e.facilityId(), in.reason().trim(), Map.of("note", threadId.toString(), "version", latest.version() + 1));
        return note(id);
    }

    @Transactional(readOnly = true)
    public List<Note> noteHistory(UUID threadId) {
        TenantContext.Tenant t = TenantContext.require();
        List<Note> all = jdbc.sql(NOTE_SQL + " WHERE org_id = ? AND thread_id = ? ORDER BY version").params(t.orgId(), threadId).query(ClinicalService::noteMap).list();
        if (all.isEmpty()) {
            throw ApiException.notFound("Note");
        }
        t.requireFacility(encounter(jdbc.sql("SELECT encounter_id FROM clinical_notes WHERE id = ?").param(all.get(0).id()).query(UUID.class).single()).facilityId());
        return all;
    }

    private static final String NOTE_SQL = "SELECT id, thread_id, version, kind, body, author_id, created_at, amend_reason FROM clinical_notes";

    private static Note noteMap(ResultSet rs, int n) throws SQLException {
        return new Note(rs.getObject("id", UUID.class), rs.getObject("thread_id", UUID.class), rs.getInt("version"), rs.getString("kind"), rs.getString("body"),
                rs.getObject("author_id", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getString("amend_reason"));
    }

    private Note note(UUID id) {
        return jdbc.sql(NOTE_SQL + " WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query(ClinicalService::noteMap).single();
    }

    /** The current text of each note thread (its latest version). */
    private List<Note> notes(UUID encounterId) {
        return jdbc.sql(NOTE_SQL + " n WHERE org_id = ? AND encounter_id = ? AND version = "
                + "(SELECT max(version) FROM clinical_notes m WHERE m.org_id = n.org_id AND m.thread_id = n.thread_id) ORDER BY created_at").params(TenantContext.require().orgId(), encounterId).query(ClinicalService::noteMap).list();
    }

    // ---- diagnoses ---------------------------------------------------------------------------

    @Transactional
    public Diagnosis addDiagnosis(UUID encounterId, DiagnosisInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Encounter e = requireOpen(encounterId);
        String kind = in.kind() == null ? "SECONDARY" : in.kind();
        String certainty = in.certainty() == null ? "PROVISIONAL" : in.certainty();
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO diagnoses (org_id, encounter_id, patient_id, facility_id, icd11_code, title, kind, certainty, recorded_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), encounterId, e.patientId(), e.facilityId(), in.icd11Code(), in.title().trim(), kind, certainty, t.practitionerId())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("diagnoses_one_primary")) {
                throw ApiException.conflict("primary_exists", "This encounter already has a primary diagnosis. Make that one secondary or rule it out first.");
            }
            throw ApiException.conflict("diagnosis_exists", "That diagnosis is already recorded on this encounter.");
        }
        audit.record("diagnosis.add", "encounter", encounterId, e.facilityId(), null, Map.of("code", in.icd11Code(), "kind", kind));
        return diagnosis(id);
    }

    @Transactional
    public Diagnosis updateDiagnosis(UUID id, DiagnosisUpdate in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID encounterId = jdbc.sql("SELECT encounter_id FROM diagnoses WHERE org_id = ? AND id = ?").params(t.orgId(), id).query(UUID.class)
                .optional().orElseThrow(() -> ApiException.notFound("Diagnosis"));
        Encounter e = requireOpen(encounterId);
        try {
            jdbc.sql("UPDATE diagnoses SET certainty = ?, kind = ?, updated_at = now() WHERE org_id = ? AND id = ?").params(in.certainty(), in.kind(), t.orgId(), id).update();
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("primary_exists", "This encounter already has a primary diagnosis.");
        }
        audit.record("diagnosis.update", "encounter", encounterId, e.facilityId(), null, Map.of("diagnosis", id.toString(), "certainty", in.certainty(), "kind", in.kind()));
        return diagnosis(id);
    }

    private Diagnosis diagnosis(UUID id) {
        return jdbc.sql(DX_SQL + " WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query(ClinicalService::dx).single();
    }

    private List<Diagnosis> diagnoses(UUID encounterId) {
        return jdbc.sql(DX_SQL + " WHERE org_id = ? AND encounter_id = ? ORDER BY (kind = 'PRIMARY') DESC, created_at").params(TenantContext.require().orgId(), encounterId)
                .query(ClinicalService::dx).list();
    }

    private static final String DX_SQL = "SELECT id, icd11_code, title, kind, certainty, recorded_by, created_at FROM diagnoses";

    private static Diagnosis dx(ResultSet rs, int n) throws SQLException {
        return new Diagnosis(rs.getObject("id", UUID.class), rs.getString("icd11_code"), rs.getString("title"), rs.getString("kind"), rs.getString("certainty"),
                rs.getObject("recorded_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    // ---- allergies ---------------------------------------------------------------------------

    @Transactional
    public Allergy addAllergy(UUID patientId, AllergyInput in) {
        TenantContext.Tenant t = TenantContext.require();
        PatientAccess.Ref p = patients.requireLive(patientId);
        UUID id = jdbc.sql("INSERT INTO allergies (org_id, patient_id, substance, category, reaction, severity, recorded_by) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id")
                .params(t.orgId(), patientId, in.substance().trim(), in.category() == null ? "DRUG" : in.category(), blank(in.reaction()), in.severity(), t.practitionerId())
                .query(UUID.class).single();
        audit.record("allergy.add", "patient", patientId, p.registeredFacilityId(), null, Map.of("severity", in.severity()));
        return allergy(id);
    }

    @Transactional
    public Allergy setAllergyStatus(UUID id, AllergyStatus in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID patientId = jdbc.sql("SELECT patient_id FROM allergies WHERE org_id = ? AND id = ?").params(t.orgId(), id).query(UUID.class)
                .optional().orElseThrow(() -> ApiException.notFound("Allergy"));
        PatientAccess.Ref p = patients.require(patientId);
        if (!"ACTIVE".equals(in.status()) && blank(in.reason()) == null) {
            throw ApiException.badRequest("reason_required", "Say why this allergy is being retired.");
        }
        jdbc.sql("UPDATE allergies SET status = ?, status_reason = ?, updated_at = now() WHERE org_id = ? AND id = ?").params(in.status(), blank(in.reason()), t.orgId(), id).update();
        audit.record("allergy.status", "patient", patientId, p.registeredFacilityId(), blank(in.reason()), Map.of("status", in.status()));
        return allergy(id);
    }

    @Transactional(readOnly = true)
    public List<Allergy> allergiesOf(UUID patientId) {
        patients.require(patientId);
        return allergies(patientId);
    }

    private List<Allergy> allergies(UUID patientId) {
        return jdbc.sql(ALLERGY_SQL + " WHERE org_id = ? AND patient_id = ? AND status <> 'ENTERED_IN_ERROR' ORDER BY (status = 'ACTIVE') DESC, created_at")
                .params(TenantContext.require().orgId(), patientId).query(ClinicalService::allergyMap).list();
    }

    private Allergy allergy(UUID id) {
        return jdbc.sql(ALLERGY_SQL + " WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query(ClinicalService::allergyMap).single();
    }

    private static final String ALLERGY_SQL = "SELECT id, substance, category, reaction, severity, status, status_reason, created_at FROM allergies";

    private static Allergy allergyMap(ResultSet rs, int n) throws SQLException {
        return new Allergy(rs.getObject("id", UUID.class), rs.getString("substance"), rs.getString("category"), rs.getString("reaction"), rs.getString("severity"),
                rs.getString("status"), rs.getString("status_reason"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    // ---- orders ------------------------------------------------------------------------------

    @Transactional
    public Order addOrder(UUID encounterId, OrderInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Encounter e = requireOpen(encounterId);
        List<String> warnings = List.of();
        String override = blank(in.allergyOverrideReason());
        if ("MEDICATION".equals(in.kind())) {
            String cadre = jdbc.sql("SELECT cadre FROM practitioners WHERE org_id = ? AND id = ?").params(t.orgId(), t.practitionerId()).query(String.class).single();
            if (!PRESCRIBERS.contains(cadre)) {
                throw ApiException.forbidden("Only a doctor, clinical officer or dentist can prescribe.");
            }
            if (blank(in.drugName()) == null || in.quantity() == null) {
                throw ApiException.badRequest("medication_incomplete", "A medication order needs a drug and a quantity.");
            }
            warnings = allergyWarnings(e.patientId(), in.drugName());
            if (!warnings.isEmpty()) {
                if (override == null || override.length() < 10) {
                    throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "allergy_conflict",
                            "Recorded allergy: " + String.join("; ", warnings) + ". To prescribe anyway, give an allergyOverrideReason of at least 10 characters.");
                }
            } else {
                override = null;
            }
        }
        UUID id = jdbc.sql("""
                INSERT INTO orders (org_id, facility_id, encounter_id, patient_id, kind, priority, description, drug_id, drug_name, dose, route, frequency,
                                    duration_days, quantity, instructions, allergy_override_reason, ordered_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(t.orgId(), e.facilityId(), encounterId, e.patientId(), in.kind(), in.priority() == null ? "ROUTINE" : in.priority(), in.description().trim(),
                        in.drugId(), blank(in.drugName()), blank(in.dose()), blank(in.route()), blank(in.frequency()), in.durationDays(), in.quantity(),
                        blank(in.instructions()), override, t.practitionerId())
                .query(UUID.class).single();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", id.toString());
        detail.put("kind", in.kind());
        if (!warnings.isEmpty()) {
            detail.put("allergyOverride", true);
        }
        audit.record("order.create", "encounter", encounterId, e.facilityId(), override, detail);
        return order(id, warnings);
    }

    @Transactional
    public Order cancelOrder(UUID id, CancelOrder in) {
        TenantContext.Tenant t = TenantContext.require();
        Order o = order(id, List.of());
        UUID encounterId = jdbc.sql("SELECT encounter_id FROM orders WHERE id = ?").param(id).query(UUID.class).single();
        Encounter e = encounter(encounterId);
        t.requireFacility(e.facilityId());
        if (!"ORDERED".equals(o.status()) || o.dispensedQuantity().signum() > 0) {
            throw ApiException.conflict("order_started", "An order that has been started or dispensed cannot be cancelled.");
        }
        jdbc.sql("UPDATE orders SET status = 'CANCELLED', cancel_reason = ?, version = version + 1, updated_at = now() WHERE org_id = ? AND id = ?")
                .params(in.reason().trim(), t.orgId(), id).update();
        audit.record("order.cancel", "encounter", encounterId, e.facilityId(), in.reason().trim(), Map.of("order", id.toString()));
        return order(id, List.of());
    }

    /** A drug name that contains, or is contained in, a recorded active allergy substance. Deliberately simple and over-cautious. */
    List<String> allergyWarnings(UUID patientId, String drugName) {
        String drug = drugName.trim().toLowerCase();
        return allergies(patientId).stream().filter(a -> "ACTIVE".equals(a.status()))
                .filter(a -> {
                    String s = a.substance().trim().toLowerCase();
                    return s.length() >= 3 && (drug.contains(s) || (drug.length() >= 3 && s.contains(drug)));
                })
                .map(a -> a.substance() + " (" + a.severity() + ")").toList();
    }

    private List<Order> orders(UUID encounterId) {
        return jdbc.sql(ORDER_SQL + " WHERE org_id = ? AND encounter_id = ? ORDER BY created_at").params(TenantContext.require().orgId(), encounterId)
                .query((rs, n) -> orderMap(rs, List.of())).list();
    }

    private Order order(UUID id, List<String> warnings) {
        return jdbc.sql(ORDER_SQL + " WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query((rs, n) -> orderMap(rs, warnings)).optional()
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    private static final String ORDER_SQL = """
            SELECT id, kind, status, priority, description, drug_id, drug_name, dose, route, frequency, duration_days, quantity, dispensed_quantity,
                   instructions, allergy_override_reason, ordered_by, cancel_reason, created_at, version FROM orders""";

    private static Order orderMap(ResultSet rs, List<String> warnings) throws SQLException {
        return new Order(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"), rs.getString("priority"), rs.getString("description"),
                rs.getObject("drug_id", UUID.class), rs.getString("drug_name"), rs.getString("dose"), rs.getString("route"), rs.getString("frequency"),
                (Integer) rs.getObject("duration_days"), rs.getBigDecimal("quantity"), rs.getBigDecimal("dispensed_quantity"), rs.getString("instructions"),
                rs.getString("allergy_override_reason"), rs.getObject("ordered_by", UUID.class), rs.getString("cancel_reason"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getInt("version"), warnings.isEmpty() ? null : warnings);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Encounter encounter(UUID id) {
        return jdbc.sql(ENC + " WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query(ClinicalService::encounter).optional()
                .orElseThrow(() -> ApiException.notFound("Encounter"));
    }

    /** The encounter exists, the caller works at its facility, the patient is visible to them, and it is still open. */
    private Encounter requireOpen(UUID id) {
        Encounter e = encounter(id);
        TenantContext.require().requireFacility(e.facilityId());
        patients.require(e.patientId());
        if (!"OPEN".equals(e.status())) {
            throw ApiException.conflict("encounter_closed", "This encounter is closed. Open a new one, or amend a note.");
        }
        return e;
    }

    private static Encounter encounter(ResultSet rs, int n) throws SQLException {
        OffsetDateTime triaged = rs.getObject("triaged_at", OffsetDateTime.class);
        OffsetDateTime ended = rs.getObject("ended_at", OffsetDateTime.class);
        return new Encounter(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getString("encounter_type"), rs.getString("status"), rs.getObject("attending_id", UUID.class), rs.getObject("appointment_id", UUID.class),
                rs.getString("chief_complaint"), rs.getString("triage_category"), triaged == null ? null : triaged.toInstant(),
                rs.getObject("started_at", OffsetDateTime.class).toInstant(), ended == null ? null : ended.toInstant(), rs.getInt("version"));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
