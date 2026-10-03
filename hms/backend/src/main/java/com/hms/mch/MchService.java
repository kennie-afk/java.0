package com.hms.mch;

import static com.hms.mch.MchModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Antenatal care, delivery outcome and childhood immunisation.
 *
 * <p>The clinical flags on an antenatal visit are worked out here from the readings, never supplied by the
 * client, so two clinics entering the same blood pressure get the same warning. They prompt a clinician; they
 * do not replace one.
 */
@Service
public class MchService {

    /** Pregnancy length used by the estimated date of delivery (Naegele's rule). */
    private static final int TERM_DAYS = 280;
    /** Beyond 43 weeks the dates are almost certainly wrong, so the entry is refused rather than stored. */
    private static final int MAX_GESTATION_DAYS = 301;

    private static final Map<String, Flag> FLAGS = Map.ofEntries(
            Map.entry("SEVERE_HYPERTENSION", new Flag("SEVERE_HYPERTENSION", "DANGER", "Blood pressure is in the severe range. Refer urgently.")),
            Map.entry("HYPERTENSION", new Flag("HYPERTENSION", "WARNING", "Blood pressure is 140/90 or higher. Recheck and assess.")),
            Map.entry("PRE_ECLAMPSIA_SIGNS", new Flag("PRE_ECLAMPSIA_SIGNS", "DANGER", "Raised blood pressure with protein in the urine. Assess for pre-eclampsia.")),
            Map.entry("SEVERE_ANAEMIA", new Flag("SEVERE_ANAEMIA", "DANGER", "Haemoglobin is below 7 g/dL.")),
            Map.entry("ANAEMIA", new Flag("ANAEMIA", "WARNING", "Haemoglobin is below 11 g/dL.")),
            Map.entry("FETAL_HEART_RATE", new Flag("FETAL_HEART_RATE", "DANGER", "Fetal heart rate is outside 110-160 beats per minute.")),
            Map.entry("FUNDAL_HEIGHT", new Flag("FUNDAL_HEIGHT", "WARNING", "Fundal height does not match the gestation by more than 3 cm.")),
            Map.entry("MALPRESENTATION", new Flag("MALPRESENTATION", "WARNING", "Not cephalic at 36 weeks or later.")),
            Map.entry("SYPHILIS_REACTIVE", new Flag("SYPHILIS_REACTIVE", "WARNING", "Syphilis screen is reactive and needs treatment.")),
            Map.entry("HIV_NEW_POSITIVE", new Flag("HIV_NEW_POSITIVE", "WARNING", "Newly HIV positive. Link to care and PMTCT.")),
            Map.entry("ADOLESCENT", new Flag("ADOLESCENT", "WARNING", "Under 18 years.")),
            Map.entry("ADVANCED_MATERNAL_AGE", new Flag("ADVANCED_MATERNAL_AGE", "WARNING", "35 years or older.")));

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;

    public MchService(JdbcClient jdbc, AuditService audit, PatientAccess patients) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
    }

    // ---- pregnancies ---------------------------------------------------------------------------

    @Transactional
    public Pregnancy open(PregnancyInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        patients.requireAlive(in.patientId());
        if (in.parity() >= in.gravida()) {
            throw ApiException.badRequest("parity_exceeds_gravida", "Parity counts earlier births, so it must be lower than gravida, which includes this pregnancy.");
        }
        LocalDate today = LocalDate.now();
        if (in.lmp().isAfter(today)) {
            throw ApiException.badRequest("lmp_in_future", "The last menstrual period cannot be in the future.");
        }
        if (ChronoUnit.DAYS.between(in.lmp(), today) > MAX_GESTATION_DAYS) {
            throw ApiException.badRequest("lmp_too_old", "That date is more than 43 weeks ago, so it cannot be an ongoing pregnancy.");
        }
        var who = jdbc.sql("SELECT sex, birth_date FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), in.patientId())
                .query((rs, n) -> new Object[] {rs.getString("sex"), rs.getObject("birth_date", LocalDate.class)}).single();
        if ("MALE".equals(who[0])) {
            throw ApiException.badRequest("not_applicable", "A pregnancy cannot be opened for a male patient.");
        }
        int age = Period.between((LocalDate) who[1], in.lmp()).getYears();
        if (age < 10 || age > 54) {
            throw ApiException.badRequest("age_out_of_range", "The patient was " + age + " at the last menstrual period, outside the range for an antenatal record.");
        }
        UUID id;
        try {
            id = jdbc.sql("INSERT INTO pregnancies (org_id, facility_id, patient_id, lmp, edd, gravida, parity, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")
                    .params(t.orgId(), in.facilityId(), in.patientId(), in.lmp(), in.lmp().plusDays(TERM_DAYS), in.gravida(), in.parity(), t.practitionerId())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("pregnancy_active", "That patient already has an ongoing pregnancy. Record its outcome before opening another.");
        }
        audit.record("pregnancy.open", "pregnancy", id, in.facilityId(), null, Map.of("patientId", in.patientId().toString()));
        return get(id);
    }

    @Transactional(readOnly = true)
    public Slice<Pregnancy> list(UUID facilityId, String status, UUID patientId, Boolean overdue, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>();
        StringBuilder sql = new StringBuilder(PREGNANCY_SQL + " WHERE pr.org_id = ? AND (NOT pt.restricted OR ?)");
        p.add(t.orgId());
        p.add(t.can(Permissions.PATIENTS_RESTRICTED));
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND pr.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND pr.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND pr.status = ?");
            p.add(status);
        }
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND pr.patient_id = ?");
            p.add(patientId);
        }
        if (Boolean.TRUE.equals(overdue)) {
            sql.append(" AND pr.status = 'ACTIVE' AND lv.next_visit_on < current_date");
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (pr.created_at, pr.id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY pr.created_at DESC, pr.id DESC LIMIT ?");
        p.add(size + 1);
        List<Pregnancy> rows = jdbc.sql(sql.toString()).params(p.toArray()).query(MchService::pregnancyRow).list();
        boolean more = rows.size() > size;
        List<Pregnancy> page = more ? rows.subList(0, size) : rows;
        String next = null;
        if (more) {
            UUID lastId = page.get(page.size() - 1).id();
            String createdAt = jdbc.sql("SELECT created_at::text FROM pregnancies WHERE org_id = ? AND id = ?").params(t.orgId(), lastId).query(String.class).single();
            next = Slice.encode(Map.of("t", createdAt, "i", lastId.toString()));
        }
        return new Slice<>(page, next);
    }

    @Transactional(readOnly = true)
    public Pregnancy get(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Pregnancy base = jdbc.sql(PREGNANCY_SQL + " WHERE pr.org_id = ? AND pr.id = ?").params(t.orgId(), id)
                .query(MchService::pregnancyRow).optional().orElseThrow(() -> ApiException.notFound("Pregnancy"));
        patients.require(base.patientId());
        t.requireFacility(base.facilityId());
        List<Visit> visits = jdbc.sql("SELECT * FROM anc_visits WHERE org_id = ? AND pregnancy_id = ? ORDER BY visit_number").params(t.orgId(), id)
                .query(MchService::visitRow).list();
        Delivery delivery = jdbc.sql("SELECT * FROM deliveries WHERE org_id = ? AND pregnancy_id = ?").params(t.orgId(), id)
                .query(MchService::deliveryRow).optional().orElse(null);
        return new Pregnancy(base.id(), base.facilityId(), base.patientId(), base.patientName(), base.lmp(), base.edd(), base.gestationWeeks(),
                base.gestationDays(), base.gravida(), base.parity(), base.status(), base.visitCount(), base.lastVisitOn(), base.nextVisitOn(),
                base.overdue(), base.flags(), visits, delivery);
    }

    // ---- antenatal visits ----------------------------------------------------------------------

    @Transactional
    public Pregnancy addVisit(UUID pregnancyId, AncVisitInput in) {
        TenantContext.Tenant t = TenantContext.require();
        var pr = lockActive(pregnancyId);
        t.requireFacility(pr.facilityId);
        LocalDate visitedOn = in.visitedOn() == null ? LocalDate.now() : in.visitedOn();
        if (visitedOn.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("visit_in_future", "A visit cannot be dated in the future.");
        }
        int gestationDays = (int) ChronoUnit.DAYS.between(pr.lmp, visitedOn);
        if (gestationDays < 0) {
            throw ApiException.badRequest("visit_before_lmp", "The visit is dated before the last menstrual period.");
        }
        if (gestationDays > MAX_GESTATION_DAYS) {
            throw ApiException.badRequest("gestation_too_long", "That visit is past 43 weeks. Check the last menstrual period.");
        }
        if ((in.systolic() == null) != (in.diastolic() == null)) {
            throw ApiException.badRequest("bp_incomplete", "Enter both blood pressure readings or neither.");
        }
        if (in.systolic() != null && in.systolic() <= in.diastolic()) {
            throw ApiException.badRequest("bp_order", "The systolic reading must be higher than the diastolic.");
        }
        if (in.nextVisitOn() != null && in.nextVisitOn().isBefore(visitedOn)) {
            throw ApiException.badRequest("next_visit_before_visit", "The next visit cannot be before this one.");
        }
        var last = jdbc.sql("SELECT visit_number, visited_on FROM anc_visits WHERE org_id = ? AND pregnancy_id = ? ORDER BY visit_number DESC LIMIT 1")
                .params(t.orgId(), pregnancyId).query((rs, n) -> new Object[] {rs.getInt("visit_number"), rs.getObject("visited_on", LocalDate.class)}).optional();
        if (last.isPresent() && visitedOn.isBefore((LocalDate) last.get()[1])) {
            throw ApiException.conflict("visit_out_of_order", "Visits are entered in date order. The last one was on " + last.get()[1] + ".");
        }
        int number = last.map(o -> (Integer) o[0] + 1).orElse(1);
        int age = Period.between(pr.birthDate, visitedOn).getYears();
        List<String> flags = flagsFor(in, gestationDays, age);

        jdbc.sql("""
                INSERT INTO anc_visits (org_id, pregnancy_id, facility_id, visit_number, visited_on, gestation_days, weight_kg, systolic, diastolic,
                  fundal_height_cm, fetal_heart_rate, presentation, haemoglobin, hiv_status, syphilis, urine_protein, iptp_given, tetanus_given,
                  iron_folate_given, risk_flags, notes, next_visit_on, recorded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(t.orgId(), pregnancyId, pr.facilityId, number, visitedOn, gestationDays, in.weightKg(), in.systolic(), in.diastolic(),
                        in.fundalHeightCm(), in.fetalHeartRate(), in.presentation(), in.haemoglobin(), in.hivStatus(), in.syphilis(), in.urineProtein(),
                        Boolean.TRUE.equals(in.iptpGiven()), Boolean.TRUE.equals(in.tetanusGiven()), Boolean.TRUE.equals(in.ironFolateGiven()),
                        flags.toArray(String[]::new), blankToNull(in.notes()), in.nextVisitOn(), t.practitionerId())
                .update();
        audit.record("anc.visit", "pregnancy", pregnancyId, pr.facilityId, null, Map.of("visit", number, "flags", flags));
        return get(pregnancyId);
    }

    static List<String> flagsFor(AncVisitInput in, int gestationDays, int ageYears) {
        List<String> out = new ArrayList<>();
        boolean raised = false;
        if (in.systolic() != null) {
            if (in.systolic() >= 160 || in.diastolic() >= 110) {
                out.add("SEVERE_HYPERTENSION");
                raised = true;
            } else if (in.systolic() >= 140 || in.diastolic() >= 90) {
                out.add("HYPERTENSION");
                raised = true;
            }
        }
        if (raised && in.urineProtein() != null && List.of("1+", "2+", "3+").contains(in.urineProtein())) {
            out.add("PRE_ECLAMPSIA_SIGNS");
        }
        if (in.haemoglobin() != null) {
            if (in.haemoglobin().doubleValue() < 7.0) {
                out.add("SEVERE_ANAEMIA");
            } else if (in.haemoglobin().doubleValue() < 11.0) {
                out.add("ANAEMIA");
            }
        }
        if (in.fetalHeartRate() != null && gestationDays >= 140 && (in.fetalHeartRate() < 110 || in.fetalHeartRate() > 160)) {
            out.add("FETAL_HEART_RATE");
        }
        if (in.fundalHeightCm() != null && gestationDays >= 140 && gestationDays <= 238
                && Math.abs(in.fundalHeightCm().doubleValue() - gestationDays / 7.0) > 3.0) {
            out.add("FUNDAL_HEIGHT");
        }
        if (gestationDays >= 252 && ("BREECH".equals(in.presentation()) || "TRANSVERSE".equals(in.presentation()))) {
            out.add("MALPRESENTATION");
        }
        if ("REACTIVE".equals(in.syphilis())) {
            out.add("SYPHILIS_REACTIVE");
        }
        if ("POSITIVE".equals(in.hivStatus())) {
            out.add("HIV_NEW_POSITIVE");
        }
        if (ageYears < 18) {
            out.add("ADOLESCENT");
        } else if (ageYears >= 35) {
            out.add("ADVANCED_MATERNAL_AGE");
        }
        return out;
    }

    // ---- delivery ------------------------------------------------------------------------------

    @Transactional
    public Pregnancy deliver(UUID pregnancyId, DeliveryInput in) {
        TenantContext.Tenant t = TenantContext.require();
        var pr = lockActive(pregnancyId);
        t.requireFacility(pr.facilityId);
        if (in.deliveredOn().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("delivery_in_future", "The delivery cannot be dated in the future.");
        }
        int gestationDays = (int) ChronoUnit.DAYS.between(pr.lmp, in.deliveredOn());
        if (gestationDays < 0 || gestationDays > MAX_GESTATION_DAYS) {
            throw ApiException.badRequest("delivery_date", "That date is not within 43 weeks of the last menstrual period.");
        }
        var lastVisit = jdbc.sql("SELECT max(visited_on) FROM anc_visits WHERE org_id = ? AND pregnancy_id = ?").params(t.orgId(), pregnancyId)
                .query(LocalDate.class).optional();
        if (lastVisit.isPresent() && in.deliveredOn().isBefore(lastVisit.get())) {
            throw ApiException.conflict("delivery_before_visit", "The delivery is dated before the last antenatal visit (" + lastVisit.get() + ").");
        }
        boolean miscarriage = "MISCARRIAGE".equals(in.outcome());
        int babies = in.babies() == null ? (miscarriage ? 0 : 1) : in.babies();
        if (miscarriage && babies != 0) {
            throw ApiException.badRequest("babies_miscarriage", "A miscarriage has no babies recorded.");
        }
        if (!miscarriage && babies < 1) {
            throw ApiException.badRequest("babies_required", "Record at least one baby for a birth.");
        }
        if (in.apgar5() != null && !"LIVE_BIRTH".equals(in.outcome())) {
            throw ApiException.badRequest("apgar_live_only", "An Apgar score applies to a live birth only.");
        }
        jdbc.sql("""
                INSERT INTO deliveries (org_id, pregnancy_id, facility_id, delivered_on, gestation_days, mode, outcome, babies, birth_weight_g, apgar_5,
                  blood_loss_ml, complications, recorded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(t.orgId(), pregnancyId, pr.facilityId, in.deliveredOn(), gestationDays, in.mode(), in.outcome(), babies, in.birthWeightG(),
                        in.apgar5(), in.bloodLossMl(), blankToNull(in.complications()), t.practitionerId()).update();
        jdbc.sql("UPDATE pregnancies SET status = ?, closed_at = now(), version = version + 1 WHERE org_id = ? AND id = ?")
                .params(miscarriage ? "LOST" : "DELIVERED", t.orgId(), pregnancyId).update();
        audit.record("pregnancy.close", "pregnancy", pregnancyId, pr.facilityId, null, Map.of("outcome", in.outcome(), "mode", in.mode()));
        return get(pregnancyId);
    }

    private record Locked(UUID facilityId, UUID patientId, LocalDate lmp, LocalDate birthDate) {}

    /** Loads an ongoing pregnancy under a row lock, so two clerks cannot record conflicting visit numbers or outcomes. */
    private Locked lockActive(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        var row = jdbc.sql("""
                SELECT pr.facility_id, pr.patient_id, pr.lmp, pr.status, pt.birth_date
                  FROM pregnancies pr JOIN patients pt ON pt.org_id = pr.org_id AND pt.id = pr.patient_id
                 WHERE pr.org_id = ? AND pr.id = ? FOR UPDATE OF pr""")
                .params(t.orgId(), id)
                .query((rs, n) -> new Object[] {rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("lmp", LocalDate.class), rs.getString("status"), rs.getObject("birth_date", LocalDate.class)})
                .optional().orElseThrow(() -> ApiException.notFound("Pregnancy"));
        patients.requireAlive((UUID) row[1]);
        if (!"ACTIVE".equals(row[3])) {
            throw ApiException.conflict("pregnancy_closed", "That pregnancy is already closed.");
        }
        return new Locked((UUID) row[0], (UUID) row[1], (LocalDate) row[2], (LocalDate) row[4]);
    }

    // ---- immunisation --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Card card(UUID patientId) {
        TenantContext.Tenant t = TenantContext.require();
        patients.require(patientId);
        var p = jdbc.sql("SELECT given_name || ' ' || family_name AS name, birth_date FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), patientId)
                .query((rs, n) -> new Object[] {rs.getString("name"), rs.getObject("birth_date", LocalDate.class)}).single();
        LocalDate birth = (LocalDate) p[1];
        Map<String, Object[]> given = new java.util.LinkedHashMap<>();
        jdbc.sql("SELECT vaccine, given_on, batch_no, site FROM immunisations WHERE org_id = ? AND patient_id = ?").params(t.orgId(), patientId)
                .query((rs, n) -> {
                    given.put(rs.getString("vaccine"), new Object[] {rs.getObject("given_on", LocalDate.class), rs.getString("batch_no"), rs.getString("site")});
                    return 0;
                }).list();
        LocalDate today = LocalDate.now();
        List<Dose> doses = new ArrayList<>();
        for (var d : ImmunisationSchedule.DOSES) {
            LocalDate dueOn = d.dueOn(birth);
            Object[] g = given.get(d.code());
            String status = g != null ? "GIVEN"
                    : today.isAfter(dueOn.plusDays(ImmunisationSchedule.GRACE_DAYS)) ? "OVERDUE"
                    : !today.isBefore(dueOn) ? "DUE" : "UPCOMING";
            doses.add(new Dose(d.code(), d.label(), d.antigen(), d.ageLabel(), dueOn, status,
                    g == null ? null : (LocalDate) g[0], g == null ? null : (String) g[1], g == null ? null : (String) g[2]));
        }
        Period age = Period.between(birth, today);
        String ageLabel = age.getYears() > 0 ? age.getYears() + " y " + age.getMonths() + " m" : age.getMonths() > 0 ? age.getMonths() + " months" : ChronoUnit.WEEKS.between(birth, today) + " weeks";
        return new Card(patientId, (String) p[0], birth, ageLabel, given.size(), ImmunisationSchedule.DOSES.size(), doses, ImmunisationSchedule.NOTE);
    }

    @Transactional
    public Card give(UUID patientId, DoseInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        patients.requireAlive(patientId);
        var dose = ImmunisationSchedule.find(in.vaccine())
                .orElseThrow(() -> ApiException.badRequest("unknown_vaccine", "'" + in.vaccine() + "' is not on the routine schedule."));
        LocalDate birth = jdbc.sql("SELECT birth_date FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), patientId).query(LocalDate.class).single();
        LocalDate on = in.givenOn() == null ? LocalDate.now() : in.givenOn();
        if (on.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("dose_in_future", "A dose cannot be dated in the future.");
        }
        if (on.isBefore(birth)) {
            throw ApiException.badRequest("dose_before_birth", "A dose cannot be dated before the child was born.");
        }
        if (dose.previous() != null) {
            LocalDate prev = jdbc.sql("SELECT given_on FROM immunisations WHERE org_id = ? AND patient_id = ? AND vaccine = ?")
                    .params(t.orgId(), patientId, dose.previous()).query(LocalDate.class).optional()
                    .orElseThrow(() -> ApiException.conflict("previous_dose_missing", dose.label() + " needs the earlier dose (" + dose.previous() + ") recorded first."));
            if (ChronoUnit.DAYS.between(prev, on) < ImmunisationSchedule.MIN_INTERVAL_DAYS) {
                throw ApiException.conflict("interval_too_short", dose.label() + " must be at least " + ImmunisationSchedule.MIN_INTERVAL_DAYS
                        + " days after the earlier dose, which was given on " + prev + ".");
            }
        }
        try {
            jdbc.sql("INSERT INTO immunisations (org_id, facility_id, patient_id, vaccine, given_on, batch_no, site, recorded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(t.orgId(), in.facilityId(), patientId, dose.code(), on, blankToNull(in.batchNo()), in.site(), t.practitionerId()).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("dose_recorded", dose.label() + " is already recorded for this child.");
        }
        audit.record("immunisation.give", "patient", patientId, in.facilityId(), null, Map.of("vaccine", dose.code(), "givenOn", on.toString()));
        return card(patientId);
    }

    /**
     * Children registered at a facility who are due or overdue for a routine dose, oldest due date first.
     * Computed from the schedule and the doses on record, so a recorded dose removes the child from the list
     * with nothing else to maintain.
     */
    @Transactional(readOnly = true)
    public Slice<DueDose> due(UUID facilityId, Integer horizonDays, Boolean overdueOnly, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        int horizon = horizonDays == null ? 0 : Math.max(0, Math.min(horizonDays, 90));
        List<Object> p = new ArrayList<>();
        StringBuilder values = new StringBuilder();
        for (var d : ImmunisationSchedule.DOSES) {
            values.append(values.length() == 0 ? "" : ", ").append("(?, ?, ?::interval)");
            p.add(d.code());
            p.add(d.label());
            p.add(d.dueAge());
        }
        StringBuilder sql = new StringBuilder("WITH sched(vaccine, label, due_age) AS (VALUES " + values + ") "
                + "SELECT * FROM (SELECT p.id AS patient_id, p.given_name || ' ' || p.family_name AS name, p.birth_date, p.phone, s.vaccine, s.label, "
                + "(p.birth_date + s.due_age)::date AS due_on FROM patients p CROSS JOIN sched s "
                + "WHERE p.org_id = ? AND p.birth_date > current_date - interval '5 years' AND p.active AND p.deceased_at IS NULL AND p.merged_into IS NULL "
                + "AND (NOT p.restricted OR ?) AND NOT EXISTS (SELECT 1 FROM immunisations i WHERE i.org_id = p.org_id AND i.patient_id = p.id AND i.vaccine = s.vaccine)");
        p.add(t.orgId());
        p.add(t.can(Permissions.PATIENTS_RESTRICTED));
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND p.registered_facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND p.registered_facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        sql.append(") d WHERE d.due_on <= current_date + ?");
        p.add(horizon);
        if (Boolean.TRUE.equals(overdueOnly)) {
            sql.append(" AND d.due_on < current_date - ?");
            p.add(ImmunisationSchedule.GRACE_DAYS);
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (d.due_on, d.patient_id, d.vaccine) > (?::date, ?::uuid, ?)");
            p.add(after.get("d"));
            p.add(after.get("p"));
            p.add(after.get("v"));
        }
        sql.append(" ORDER BY d.due_on, d.patient_id, d.vaccine LIMIT ?");
        p.add(size + 1);
        LocalDate today = LocalDate.now();
        List<DueDose> rows = jdbc.sql(sql.toString()).params(p.toArray()).query((rs, n) -> {
            LocalDate dueOn = rs.getObject("due_on", LocalDate.class);
            int late = (int) ChronoUnit.DAYS.between(dueOn, today);
            String status = late > ImmunisationSchedule.GRACE_DAYS ? "OVERDUE" : late >= 0 ? "DUE" : "UPCOMING";
            return new DueDose(rs.getObject("patient_id", UUID.class), rs.getString("name"), rs.getObject("birth_date", LocalDate.class),
                    rs.getString("vaccine"), rs.getString("label"), dueOn, Math.max(late, 0), status, rs.getString("phone"));
        }).list();
        boolean more = rows.size() > size;
        List<DueDose> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("d", page.get(page.size() - 1).dueOn().toString(), "p", page.get(page.size() - 1).patientId().toString(),
                "v", page.get(page.size() - 1).vaccine())) : null;
        return new Slice<>(page, next);
    }

    // ---- mapping -------------------------------------------------------------------------------

    private static final String PREGNANCY_SQL = """
            SELECT pr.id, pr.facility_id, pr.patient_id, pt.given_name || ' ' || pt.family_name AS patient_name, pr.lmp, pr.edd, pr.gravida, pr.parity,
                   pr.status, pr.closed_at,
                   (SELECT count(*) FROM anc_visits v WHERE v.org_id = pr.org_id AND v.pregnancy_id = pr.id) AS visit_count,
                   lv.visited_on AS last_visit_on, lv.next_visit_on, lv.risk_flags,
                   (SELECT d.delivered_on FROM deliveries d WHERE d.org_id = pr.org_id AND d.pregnancy_id = pr.id) AS delivered_on
              FROM pregnancies pr
              JOIN patients pt ON pt.org_id = pr.org_id AND pt.id = pr.patient_id
              LEFT JOIN LATERAL (SELECT visited_on, next_visit_on, risk_flags FROM anc_visits v
                                  WHERE v.org_id = pr.org_id AND v.pregnancy_id = pr.id ORDER BY visit_number DESC LIMIT 1) lv ON true""";

    private static Pregnancy pregnancyRow(ResultSet rs, int n) throws SQLException {
        LocalDate lmp = rs.getObject("lmp", LocalDate.class);
        String status = rs.getString("status");
        LocalDate end = status.equals("ACTIVE") ? LocalDate.now() : rs.getObject("delivered_on", LocalDate.class);
        int days = (int) ChronoUnit.DAYS.between(lmp, end == null ? LocalDate.now() : end);
        LocalDate next = rs.getObject("next_visit_on", LocalDate.class);
        return new Pregnancy(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getString("patient_name"), lmp, rs.getObject("edd", LocalDate.class), days / 7, days % 7, rs.getInt("gravida"), rs.getInt("parity"),
                status, rs.getInt("visit_count"), rs.getObject("last_visit_on", LocalDate.class), next,
                status.equals("ACTIVE") && next != null && next.isBefore(LocalDate.now()), flagList(rs.getArray("risk_flags")), null, null);
    }

    private static Visit visitRow(ResultSet rs, int n) throws SQLException {
        int days = rs.getInt("gestation_days");
        return new Visit(rs.getObject("id", UUID.class), rs.getInt("visit_number"), rs.getObject("visited_on", LocalDate.class), days / 7, days % 7,
                rs.getBigDecimal("weight_kg"), (Integer) rs.getObject("systolic"), (Integer) rs.getObject("diastolic"), rs.getBigDecimal("fundal_height_cm"),
                (Integer) rs.getObject("fetal_heart_rate"), rs.getString("presentation"), rs.getBigDecimal("haemoglobin"), rs.getString("hiv_status"),
                rs.getString("syphilis"), rs.getString("urine_protein"), rs.getBoolean("iptp_given"), rs.getBoolean("tetanus_given"),
                rs.getBoolean("iron_folate_given"), flagList(rs.getArray("risk_flags")), rs.getString("notes"), rs.getObject("next_visit_on", LocalDate.class));
    }

    private static Delivery deliveryRow(ResultSet rs, int n) throws SQLException {
        return new Delivery(rs.getObject("id", UUID.class), rs.getObject("delivered_on", LocalDate.class), rs.getInt("gestation_days") / 7, rs.getString("mode"),
                rs.getString("outcome"), rs.getInt("babies"), (Integer) rs.getObject("birth_weight_g"), (Integer) rs.getObject("apgar_5"),
                (Integer) rs.getObject("blood_loss_ml"), rs.getString("complications"));
    }

    private static List<Flag> flagList(java.sql.Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        List<Flag> out = new ArrayList<>();
        for (Object code : (Object[]) array.getArray()) {
            Flag f = FLAGS.get(String.valueOf(code));
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
