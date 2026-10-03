package com.hms.mch;

import static com.hms.mch.MchModels.Flag;
import static com.hms.mch.PostnatalModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Family planning: which method a patient is on, when the next contact is due, and the visits behind that.
 *
 * <p>The current method is never stored: it is the method on the latest visit, so there is nothing to keep in step.
 * A mistake is corrected by a later visit, because visits are append-only.
 */
@Service
public class FamilyPlanningService {

    /** DMPA-IM is given every 3 months (13 weeks); the other methods have no single interval, so the clinician sets the date. */
    static final int DMPA_INTERVAL_DAYS = 91;

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("COC", "Combined oral pill"), Map.entry("POP", "Progestogen-only pill"), Map.entry("DMPA", "Injectable (DMPA)"),
            Map.entry("IMPLANT", "Implant"), Map.entry("IUCD", "Intrauterine device"), Map.entry("MALE_CONDOM", "Male condom"),
            Map.entry("FEMALE_CONDOM", "Female condom"), Map.entry("TUBAL_LIGATION", "Tubal ligation"), Map.entry("VASECTOMY", "Vasectomy"),
            Map.entry("NATURAL", "Natural methods"), Map.entry("EMERGENCY", "Emergency contraception"), Map.entry("NONE", "No method"));

    /** Methods that do not suit a patient who is currently pregnant. Condoms and emergency contraception are not on the list. */
    private static final Set<String> NOT_IN_PREGNANCY = Set.of("COC", "POP", "DMPA", "IMPLANT", "IUCD", "TUBAL_LIGATION", "NATURAL");
    private static final Set<String> FEMALE_ONLY = Set.of("COC", "POP", "DMPA", "IMPLANT", "IUCD", "FEMALE_CONDOM", "TUBAL_LIGATION", "EMERGENCY");

    private static final Map<String, Flag> FLAGS = Map.of(
            "COC_SEVERE_BP", new Flag("COC_SEVERE_BP", "DANGER", "Blood pressure is 160/100 or higher. Combined pills are generally not recommended."),
            "COC_RAISED_BP", new Flag("COC_RAISED_BP", "WARNING", "Blood pressure is 140/90 or higher. Combined pills are generally not advised; consider another method."));

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;

    public FamilyPlanningService(JdbcClient jdbc, AuditService audit, PatientAccess patients) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public FamilyPlanning get(UUID patientId) {
        TenantContext.Tenant t = TenantContext.require();
        patients.require(patientId);
        String name = jdbc.sql("SELECT given_name || ' ' || family_name FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), patientId).query(String.class).single();
        List<FamilyPlanningVisit> visits = jdbc.sql("SELECT * FROM family_planning_visits WHERE org_id = ? AND patient_id = ? ORDER BY visited_on DESC, recorded_at DESC, id DESC")
                .params(t.orgId(), patientId).query(FamilyPlanningService::visitRow).list();
        String current = visits.isEmpty() || visits.get(0).method().equals("NONE") ? null : visits.get(0).method();
        LocalDate next = current == null ? null : visits.get(0).nextDueOn();
        return new FamilyPlanning(patientId, name, current, current == null ? null : LABELS.get(current), next, next != null && next.isBefore(LocalDate.now()), visits);
    }

    @Transactional
    public FamilyPlanning record(UUID patientId, FamilyPlanningInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        patients.requireAlive(patientId);
        // Serialise visits for one patient, so two clerks cannot both start a method.
        jdbc.sql("SELECT id FROM patients WHERE org_id = ? AND id = ? FOR UPDATE").params(t.orgId(), patientId).query(UUID.class).single();
        var who = jdbc.sql("SELECT sex, birth_date FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), patientId)
                .query((rs, n) -> new Object[] {rs.getString("sex"), rs.getObject("birth_date", LocalDate.class)}).single();
        LocalDate visitedOn = in.visitedOn() == null ? LocalDate.now() : in.visitedOn();
        if (visitedOn.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("visit_in_future", "A visit cannot be dated in the future.");
        }
        if (visitedOn.isBefore((LocalDate) who[1])) {
            throw ApiException.badRequest("visit_before_birth", "A visit cannot be dated before the patient was born.");
        }
        if ((in.systolic() == null) != (in.diastolic() == null)) {
            throw ApiException.badRequest("bp_incomplete", "Enter both blood pressure readings or neither.");
        }
        if (in.systolic() != null && in.systolic() <= in.diastolic()) {
            throw ApiException.badRequest("bp_order", "The systolic reading must be higher than the diastolic.");
        }
        boolean stopping = in.visitType().equals("DISCONTINUE");
        if (stopping != in.method().equals("NONE")) {
            throw ApiException.badRequest("method_visit_type", stopping ? "Stopping a method is recorded with the method NONE." : "NONE is only used when a method is stopped.");
        }
        String sex = (String) who[0];
        if (FEMALE_ONLY.contains(in.method()) && sex.equals("MALE")) {
            throw ApiException.badRequest("method_not_applicable", LABELS.get(in.method()) + " cannot be recorded for a male patient.");
        }
        if (in.method().equals("VASECTOMY") && sex.equals("FEMALE")) {
            throw ApiException.badRequest("method_not_applicable", "Vasectomy cannot be recorded for a female patient.");
        }
        if (in.nextDueOn() != null && in.nextDueOn().isBefore(visitedOn)) {
            throw ApiException.badRequest("next_due_before_visit", "The next contact cannot be before this one.");
        }
        var last = jdbc.sql("SELECT method, visited_on FROM family_planning_visits WHERE org_id = ? AND patient_id = ? ORDER BY visited_on DESC, recorded_at DESC, id DESC LIMIT 1")
                .params(t.orgId(), patientId).query((rs, n) -> new Object[] {rs.getString("method"), rs.getObject("visited_on", LocalDate.class)}).optional();
        if (last.isPresent() && visitedOn.isBefore((LocalDate) last.get()[1])) {
            throw ApiException.conflict("visit_out_of_order", "Visits are entered in date order. The last one was on " + last.get()[1] + ".");
        }
        String current = last.isEmpty() || last.get()[0].equals("NONE") ? null : (String) last.get()[0];
        switch (in.visitType()) {
            case "NEW" -> {
                if (current != null) {
                    throw ApiException.conflict("already_on_method", "The patient is already using " + LABELS.get(current) + ". Record a revisit or a switch.");
                }
            }
            case "REVISIT" -> {
                if (current == null) {
                    throw ApiException.conflict("no_current_method", "The patient has no method on record. Record a new start.");
                }
                if (!current.equals(in.method())) {
                    throw ApiException.badRequest("revisit_method", "A revisit continues " + LABELS.get(current) + ". Use a switch to change method.");
                }
            }
            case "SWITCH" -> {
                if (current == null) {
                    throw ApiException.conflict("no_current_method", "The patient has no method on record. Record a new start.");
                }
                if (current.equals(in.method())) {
                    throw ApiException.badRequest("switch_same_method", "That is the method the patient already uses. Record a revisit.");
                }
            }
            default -> {
                if (current == null) {
                    throw ApiException.conflict("no_current_method", "The patient has no method on record to stop.");
                }
            }
        }
        if (NOT_IN_PREGNANCY.contains(in.method()) && !stopping && jdbc.sql("SELECT count(*) FROM pregnancies WHERE org_id = ? AND patient_id = ? AND status = 'ACTIVE'")
                .params(t.orgId(), patientId).query(Long.class).single() > 0) {
            throw ApiException.conflict("pregnancy_active", LABELS.get(in.method()) + " cannot be started during an ongoing pregnancy.");
        }
        LocalDate nextDue = in.nextDueOn();
        if (nextDue == null && in.method().equals("DMPA")) {
            nextDue = visitedOn.plusDays(DMPA_INTERVAL_DAYS);
        }
        if (nextDue != null && List.of("TUBAL_LIGATION", "VASECTOMY", "NONE").contains(in.method())) {
            throw ApiException.badRequest("no_follow_up_date", "This method has no repeat contact, so a next date does not apply.");
        }
        List<String> flags = new ArrayList<>();
        if (in.method().equals("COC") && in.systolic() != null) {
            if (in.systolic() >= 160 || in.diastolic() >= 100) {
                flags.add("COC_SEVERE_BP");
            } else if (in.systolic() >= 140 || in.diastolic() >= 90) {
                flags.add("COC_RAISED_BP");
            }
        }
        jdbc.sql("""
                INSERT INTO family_planning_visits (org_id, facility_id, patient_id, visited_on, visit_type, method, systolic, diastolic, weight_kg,
                  next_due_on, risk_flags, notes, recorded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(t.orgId(), in.facilityId(), patientId, visitedOn, in.visitType(), in.method(), in.systolic(), in.diastolic(), in.weightKg(), nextDue,
                        flags.toArray(String[]::new), in.notes() == null || in.notes().isBlank() ? null : in.notes().trim(), t.practitionerId()).update();
        audit.record("familyplanning.visit", "patient", patientId, in.facilityId(), null, Map.of("type", in.visitType(), "method", in.method()));
        return get(patientId);
    }

    /** Patients whose next family planning contact is due or overdue, oldest first. */
    @Transactional(readOnly = true)
    public Slice<FamilyPlanningDue> due(UUID facilityId, Integer horizonDays, Boolean overdueOnly, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        int horizon = horizonDays == null ? 0 : Math.max(0, Math.min(horizonDays, 90));
        List<Object> p = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM (
                  SELECT DISTINCT ON (v.patient_id) v.patient_id, pt.given_name || ' ' || pt.family_name AS name, pt.phone, v.method, v.next_due_on, v.facility_id
                    FROM family_planning_visits v JOIN patients pt ON pt.org_id = v.org_id AND pt.id = v.patient_id
                   WHERE v.org_id = ? AND (NOT pt.restricted OR ?) AND pt.active AND pt.deceased_at IS NULL AND pt.merged_into IS NULL
                   ORDER BY v.patient_id, v.visited_on DESC, v.recorded_at DESC, v.id DESC) latest
                 WHERE latest.method <> 'NONE' AND latest.next_due_on IS NOT NULL AND latest.next_due_on <= current_date + ?""");
        p.add(t.orgId());
        p.add(t.can(Permissions.PATIENTS_RESTRICTED));
        p.add(horizon);
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND latest.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND latest.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (Boolean.TRUE.equals(overdueOnly)) {
            sql.append(" AND latest.next_due_on < current_date");
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (latest.next_due_on, latest.patient_id) > (?::date, ?::uuid)");
            p.add(after.get("d"));
            p.add(after.get("p"));
        }
        sql.append(" ORDER BY latest.next_due_on, latest.patient_id LIMIT ?");
        p.add(size + 1);
        LocalDate today = LocalDate.now();
        List<FamilyPlanningDue> rows = jdbc.sql(sql.toString()).params(p.toArray()).query((rs, n) -> {
            LocalDate dueOn = rs.getObject("next_due_on", LocalDate.class);
            return new FamilyPlanningDue(rs.getObject("patient_id", UUID.class), rs.getString("name"), rs.getString("method"), LABELS.get(rs.getString("method")), dueOn,
                    (int) Math.max(0, ChronoUnit.DAYS.between(dueOn, today)), rs.getString("phone"));
        }).list();
        boolean more = rows.size() > size;
        List<FamilyPlanningDue> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("d", page.get(page.size() - 1).nextDueOn().toString(), "p", page.get(page.size() - 1).patientId().toString())) : null;
        return new Slice<>(page, next);
    }

    private static FamilyPlanningVisit visitRow(ResultSet rs, int n) throws SQLException {
        List<Flag> flags = new ArrayList<>();
        java.sql.Array a = rs.getArray("risk_flags");
        if (a != null) {
            for (Object code : (Object[]) a.getArray()) {
                Flag f = FLAGS.get(String.valueOf(code));
                if (f != null) {
                    flags.add(f);
                }
            }
        }
        String method = rs.getString("method");
        return new FamilyPlanningVisit(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("visited_on", LocalDate.class),
                rs.getString("visit_type"), method, LABELS.get(method), (Integer) rs.getObject("systolic"), (Integer) rs.getObject("diastolic"),
                rs.getBigDecimal("weight_kg"), rs.getObject("next_due_on", LocalDate.class), flags, rs.getString("notes"));
    }
}
