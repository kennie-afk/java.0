package com.hms.programmes;

import static com.hms.programmes.ProgrammeModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * Long-term care programmes. A person is in one programme once at a time; follow-up visits move the next
 * appointment date; anyone whose appointment is overdue past the grace period is a defaulter to trace.
 *
 * <p>The grace period is a local setting (default 7 days), not the Ministry of Health's definition of loss
 * to follow-up. HIV care needs its own permission, and every opening of an HIV enrolment is audited.
 */
@Service
public class ProgrammeService {

    public static final int DEFAULT_GRACE_DAYS = 7;
    private static final int MAX_GRACE_DAYS = 365;

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;

    public ProgrammeService(JdbcClient jdbc, AuditService audit, PatientAccess patients) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
    }

    // ---- enrolment ---------------------------------------------------------------------------

    @Transactional
    public Enrolment enrol(EnrolInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        requireProgramme(t, in.programme());
        patients.requireAlive(in.patientId());
        LocalDate today = LocalDate.now();
        LocalDate on = in.enrolledOn() == null ? today : in.enrolledOn();
        if (on.isAfter(today)) {
            throw ApiException.badRequest("enrolled_in_future", "The enrolment date cannot be in the future.");
        }
        if (in.nextVisitOn() != null && !in.nextVisitOn().isAfter(on)) {
            throw ApiException.badRequest("next_visit_before_enrolment", "The next visit must be after the enrolment date.");
        }
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO programme_enrolments (org_id, facility_id, patient_id, programme, register_no, enrolled_on, regimen, next_visit_on, enrolled_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), in.facilityId(), in.patientId(), in.programme(), nextRegisterNo(t, in.facilityId(), in.programme()), on, blank(in.regimen()), in.nextVisitOn(),
                            t.practitionerId()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("already_enrolled", "That patient is already enrolled in this programme. Record an outcome before enrolling again.");
        }
        audit.record("programme.enrol", "patient", in.patientId(), in.facilityId(), null, Map.of("programme", in.programme()));
        return load(id, true);
    }

    @Transactional
    public Enrolment visit(UUID id, VisitInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Ctx c = lock(id);
        t.requireFacility(c.facilityId);
        requireProgramme(t, c.programme);
        if (!"ACTIVE".equals(c.status)) {
            throw ApiException.conflict("not_active", "Visits are recorded for an active enrolment (this one is " + c.status + ").");
        }
        LocalDate today = LocalDate.now();
        if (in.visitedOn().isAfter(today)) {
            throw ApiException.badRequest("visit_in_future", "A visit cannot be dated in the future.");
        }
        if (in.visitedOn().isBefore(c.enrolledOn)) {
            throw ApiException.badRequest("visit_before_enrolment", "A visit cannot be dated before the enrolment.");
        }
        if ((in.systolic() == null) != (in.diastolic() == null)) {
            throw ApiException.badRequest("bp_incomplete", "Blood pressure needs both the systolic and the diastolic reading.");
        }
        if (in.systolic() != null && in.systolic() <= in.diastolic()) {
            throw ApiException.badRequest("bp_order", "The systolic reading must be higher than the diastolic.");
        }
        if (in.nextVisitOn() != null && !in.nextVisitOn().isAfter(in.visitedOn())) {
            throw ApiException.badRequest("next_visit_before_visit", "The next visit must be after this visit.");
        }
        jdbc.sql("""
                INSERT INTO programme_visits (org_id, enrolment_id, visited_on, weight_kg, systolic, diastolic, glucose_mmol, adherence, regimen, next_visit_on, notes, recorded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(t.orgId(), id, in.visitedOn(), in.weightKg(), in.systolic(), in.diastolic(), in.glucoseMmol(), in.adherence(), blank(in.regimen()), in.nextVisitOn(),
                        blank(in.notes()), t.practitionerId()).update();
        // The latest visit decides the appointment: a visit with no next date clears it rather than leaving a stale one.
        jdbc.sql("UPDATE programme_enrolments SET next_visit_on = ?, regimen = COALESCE(?, regimen), version = version + 1 WHERE org_id = ? AND id = ?")
                .params(in.nextVisitOn(), blank(in.regimen()), t.orgId(), id).update();
        audit.record("programme.visit", "patient", c.patientId, c.facilityId, null, Map.of("programme", c.programme));
        return load(id, true);
    }

    @Transactional
    public Enrolment outcome(UUID id, OutcomeInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Ctx c = lock(id);
        t.requireFacility(c.facilityId);
        requireProgramme(t, c.programme);
        if (!"ACTIVE".equals(c.status)) {
            throw ApiException.conflict("not_active", "That enrolment already has an outcome (" + c.status + ").");
        }
        LocalDate today = LocalDate.now();
        LocalDate on = in.outcomeOn() == null ? today : in.outcomeOn();
        if (on.isAfter(today) || on.isBefore(c.enrolledOn)) {
            throw ApiException.badRequest("outcome_date", "The outcome date must be between the enrolment date and today.");
        }
        jdbc.sql("UPDATE programme_enrolments SET status = ?, outcome_on = ?, outcome_note = ?, next_visit_on = NULL, version = version + 1 WHERE org_id = ? AND id = ?")
                .params(in.status(), on, in.note().trim(), t.orgId(), id).update();
        audit.record("programme.outcome", "patient", c.patientId, c.facilityId, in.note().trim(), Map.of("programme", c.programme, "status", in.status()));
        return load(id, false);
    }

    // ---- reading -----------------------------------------------------------------------------

    @Transactional
    public Enrolment open(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Enrolment e = load(id, true);
        t.requireFacility(e.facilityId());
        requireProgramme(t, e.programme());
        patients.require(e.patientId());
        if ("HIV".equals(e.programme())) {
            audit.record("programme.view", "patient", e.patientId(), e.facilityId(), null, Map.of("programme", "HIV"));
        }
        return e;
    }

    @Transactional(readOnly = true)
    public Slice<Row> list(UUID facilityId, UUID patientId, String programme, String status, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(t.orgId()));
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.patient_id, p.given_name || ' ' || p.family_name AS patient_name, e.programme, e.register_no, e.enrolled_on, e.status, e.next_visit_on, e.created_at
                  FROM programme_enrolments e JOIN patients p ON p.org_id = e.org_id AND p.id = e.patient_id WHERE e.org_id = ?""");
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND e.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND e.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND e.patient_id = ?");
            p.add(patientId);
        }
        if (programme != null && !programme.isBlank()) {
            sql.append(" AND e.programme = ?");
            p.add(programme);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status = ?");
            p.add(status);
        }
        sql.append(" AND (e.programme <> 'HIV' OR ?)");
        p.add(t.can(Permissions.PROGRAMMES_HIV));
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (e.created_at, e.id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY e.created_at DESC, e.id DESC LIMIT ?");
        p.add(size + 1);
        LocalDate today = LocalDate.now();
        List<Object[]> raw = new ArrayList<>();
        List<Row> rows = jdbc.sql(sql.toString()).params(p.toArray()).query((rs, n) -> {
            raw.add(new Object[] {rs.getObject("created_at", OffsetDateTime.class).toInstant().toString(), rs.getObject("id", UUID.class).toString()});
            LocalDate next = rs.getObject("next_visit_on", LocalDate.class);
            String st = rs.getString("status");
            return new Row(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"), rs.getString("programme"), rs.getString("register_no"),
                    rs.getObject("enrolled_on", LocalDate.class), st, next, overdue(st, next, today));
        }).list();
        boolean more = rows.size() > size;
        List<Row> page = more ? rows.subList(0, size) : rows;
        String nextCursor = more ? Slice.encode(Map.of("t", (String) raw.get(size - 1)[0], "i", (String) raw.get(size - 1)[1])) : null;
        return new Slice<>(page, nextCursor);
    }

    /** Active enrolments whose next visit is more than the grace period past, worst first. Includes the phone to trace by. */
    @Transactional(readOnly = true)
    public List<Defaulter> defaulters(UUID facilityId, String programme, Integer graceDays) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        int grace = grace(graceDays);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), facilityId, LocalDate.now().minusDays(grace), t.can(Permissions.PROGRAMMES_HIV)));
        String sql = """
                SELECT e.id, e.patient_id, p.given_name || ' ' || p.family_name AS patient_name, p.phone, e.programme, e.register_no, e.next_visit_on
                  FROM programme_enrolments e JOIN patients p ON p.org_id = e.org_id AND p.id = e.patient_id
                 WHERE e.org_id = ? AND e.facility_id = ? AND e.status = 'ACTIVE' AND e.next_visit_on < ? AND p.deceased_at IS NULL AND (e.programme <> 'HIV' OR ?)""";
        if (programme != null && !programme.isBlank()) {
            sql += " AND e.programme = ?";
            p.add(programme);
        }
        LocalDate today = LocalDate.now();
        return jdbc.sql(sql + " ORDER BY e.next_visit_on, e.id LIMIT 500").params(p.toArray())
                .query((rs, n) -> new Defaulter(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"), rs.getString("phone"),
                        rs.getString("programme"), rs.getString("register_no"), rs.getObject("next_visit_on", LocalDate.class),
                        (int) ChronoUnit.DAYS.between(rs.getObject("next_visit_on", LocalDate.class), today))).list();
    }

    /** Counts only: no names. Active and overdue are as of today; outcomes are those recorded within the period. */
    @Transactional(readOnly = true)
    public Summary summary(UUID facilityId, LocalDate from, LocalDate to, Integer graceDays) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("period_reversed", "The start of the period is after its end.");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            throw ApiException.badRequest("period_too_long", "A period is at most 366 days.");
        }
        int grace = grace(graceDays);
        LocalDate cutoff = LocalDate.now().minusDays(grace);
        List<ProgrammeCount> counts = jdbc.sql("""
                SELECT e.programme, count(*) FILTER (WHERE e.status = 'ACTIVE')::int AS active,
                       count(*) FILTER (WHERE e.status = 'ACTIVE' AND e.next_visit_on < ?)::int AS missed,
                       count(*) FILTER (WHERE e.status <> 'ACTIVE' AND e.outcome_on BETWEEN ? AND ?)::int AS outcomes
                  FROM programme_enrolments e WHERE e.org_id = ? AND e.facility_id = ? AND (e.programme <> 'HIV' OR ?)
                 GROUP BY e.programme ORDER BY e.programme""").params(cutoff, start, end, t.orgId(), facilityId, t.can(Permissions.PROGRAMMES_HIV))
                .query((rs, n) -> new ProgrammeCount(rs.getString("programme"), rs.getInt("active"), rs.getInt("missed"), rs.getInt("outcomes"))).list();
        return new Summary(facilityId, start, end, grace, counts);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static int grace(Integer requested) {
        if (requested == null) {
            return DEFAULT_GRACE_DAYS;
        }
        if (requested < 0 || requested > MAX_GRACE_DAYS) {
            throw ApiException.badRequest("grace_range", "The grace period is between 0 and " + MAX_GRACE_DAYS + " days.");
        }
        return requested;
    }

    private static void requireProgramme(TenantContext.Tenant t, String programme) {
        if ("HIV".equals(programme) && !t.can(Permissions.PROGRAMMES_HIV)) {
            throw ApiException.forbidden("HIV care records need the HIV programme permission.");
        }
    }

    private static Integer overdue(String status, LocalDate next, LocalDate today) {
        if (!"ACTIVE".equals(status) || next == null || !next.isBefore(today)) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(next, today);
    }

    private record Ctx(UUID id, UUID facilityId, UUID patientId, String programme, String status, LocalDate enrolledOn) {}

    private Ctx lock(UUID id) {
        return jdbc.sql("SELECT id, facility_id, patient_id, programme, status, enrolled_on FROM programme_enrolments WHERE org_id = ? AND id = ? FOR UPDATE")
                .params(TenantContext.require().orgId(), id)
                .query((rs, n) -> new Ctx(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("programme"),
                        rs.getString("status"), rs.getObject("enrolled_on", LocalDate.class))).optional().orElseThrow(() -> ApiException.notFound("Enrolment"));
    }

    private String nextRegisterNo(TenantContext.Tenant t, UUID facilityId, String programme) {
        String counter = "PRG-" + programme;
        jdbc.sql("INSERT INTO facility_counters (org_id, facility_id, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").params(t.orgId(), facilityId, counter).update();
        long n = jdbc.sql("UPDATE facility_counters SET next_value = next_value + 1 WHERE facility_id = ? AND name = ? RETURNING next_value - 1").params(facilityId, counter).query(Long.class).single();
        return String.format("%s-%05d", programme, n);
    }

    private Enrolment load(UUID id, boolean withVisits) {
        TenantContext.Tenant t = TenantContext.require();
        Enrolment base = jdbc.sql("""
                SELECT e.id, e.facility_id, e.patient_id, p.given_name || ' ' || p.family_name AS patient_name, e.programme, e.register_no, e.enrolled_on, e.status, e.regimen,
                       e.next_visit_on, e.outcome_on, e.outcome_note
                  FROM programme_enrolments e JOIN patients p ON p.org_id = e.org_id AND p.id = e.patient_id WHERE e.org_id = ? AND e.id = ?""").params(t.orgId(), id)
                .query((rs, n) -> {
                    LocalDate next = rs.getObject("next_visit_on", LocalDate.class);
                    String st = rs.getString("status");
                    return new Enrolment(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                            rs.getString("programme"), rs.getString("register_no"), rs.getObject("enrolled_on", LocalDate.class), st, rs.getString("regimen"), next,
                            rs.getObject("outcome_on", LocalDate.class), rs.getString("outcome_note"), overdue(st, next, LocalDate.now()), List.of());
                }).optional().orElseThrow(() -> ApiException.notFound("Enrolment"));
        requireProgramme(t, base.programme());
        List<Visit> visits = !withVisits ? List.of() : jdbc.sql("""
                SELECT id, visited_on, weight_kg, systolic, diastolic, glucose_mmol, adherence, regimen, next_visit_on, notes, recorded_at
                  FROM programme_visits WHERE org_id = ? AND enrolment_id = ? ORDER BY visited_on DESC, recorded_at DESC LIMIT 200""").params(t.orgId(), id)
                .query((rs, n) -> new Visit(rs.getObject("id", UUID.class), rs.getObject("visited_on", LocalDate.class), rs.getBigDecimal("weight_kg"), (Integer) rs.getObject("systolic"),
                        (Integer) rs.getObject("diastolic"), rs.getBigDecimal("glucose_mmol"), rs.getString("adherence"), rs.getString("regimen"), rs.getObject("next_visit_on", LocalDate.class),
                        rs.getString("notes"), instant(rs, "recorded_at"))).list();
        return new Enrolment(base.id(), base.facilityId(), base.patientId(), base.patientName(), base.programme(), base.registerNo(), base.enrolledOn(), base.status(), base.regimen(),
                base.nextVisitOn(), base.outcomeOn(), base.outcomeNote(), base.daysOverdue(), visits);
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
