package com.hms.scheduling;

import static com.hms.scheduling.SchedulingModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clinics, availability and appointments. Double booking is prevented by the database (an exclusion
 * constraint), not by a check-then-insert that two requests could both pass.
 */
@Service
public class SchedulingService {

    private static final Map<String, Set<String>> NEXT = Map.of(
            "BOOKED", Set.of("CHECKED_IN", "CANCELLED", "NO_SHOW"),
            "CHECKED_IN", Set.of("IN_PROGRESS", "CANCELLED", "NO_SHOW"),
            "IN_PROGRESS", Set.of("COMPLETED"),
            "COMPLETED", Set.of(), "CANCELLED", Set.of(), "NO_SHOW", Set.of());

    private static final String SELECT = """
            SELECT a.id, a.facility_id, a.clinic_id, a.patient_id, p.given_name || ' ' || p.family_name AS patient_name, a.practitioner_id,
                   a.starts_at, a.ends_at, a.status, a.priority, a.walk_in, a.reason, a.queue_number, a.checked_in_at, a.cancel_reason, a.version
              FROM appointments a JOIN patients p ON p.org_id = a.org_id AND p.id = a.patient_id""";

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;

    public SchedulingService(JdbcClient jdbc, AuditService audit, PatientAccess patients) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
    }

    // ---- clinics -----------------------------------------------------------------------------

    @Transactional
    public Clinic createClinic(ClinicInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        UUID id;
        try {
            id = jdbc.sql("INSERT INTO clinics (org_id, facility_id, name, specialty, slot_minutes) VALUES (?, ?, ?, ?, ?) RETURNING id")
                    .params(t.orgId(), in.facilityId(), in.name().trim(), in.specialty(), in.slotMinutes() == null ? 15 : in.slotMinutes())
                    .query(UUID.class).single();
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiException.conflict("clinic_exists", "That facility already has a clinic with that name.");
        }
        writeSessions(t, id, in.facilityId(), in.sessions());
        audit.record("clinic.create", "clinic", id, in.facilityId(), null, Map.of("name", in.name().trim()));
        return clinic(id);
    }

    @Transactional
    public Clinic updateClinic(UUID id, ClinicUpdate in) {
        TenantContext.Tenant t = TenantContext.require();
        Clinic existing = clinic(id);
        t.requireFacility(existing.facilityId());
        jdbc.sql("UPDATE clinics SET name = ?, specialty = ?, slot_minutes = ?, active = ? WHERE org_id = ? AND id = ?")
                .params(in.name().trim(), in.specialty(), in.slotMinutes() == null ? existing.slotMinutes() : in.slotMinutes(), in.active(), t.orgId(), id).update();
        jdbc.sql("DELETE FROM clinic_sessions WHERE org_id = ? AND clinic_id = ?").params(t.orgId(), id).update();
        writeSessions(t, id, existing.facilityId(), in.sessions());
        audit.record("clinic.update", "clinic", id, existing.facilityId(), null, Map.of("active", in.active()));
        return clinic(id);
    }

    private void writeSessions(TenantContext.Tenant t, UUID clinicId, UUID facilityId, List<SessionInput> sessions) {
        if (sessions == null) {
            return;
        }
        for (SessionInput s : sessions) {
            if (!s.endTime().isAfter(s.startTime())) {
                throw ApiException.badRequest("bad_session", "A session must end after it starts.");
            }
            Long works = jdbc.sql("SELECT count(*) FROM practitioner_facilities WHERE practitioner_id = ? AND facility_id = ?")
                    .params(s.practitionerId(), facilityId).query(Long.class).single();
            if (works == 0) {
                throw ApiException.badRequest("not_at_facility", "That practitioner is not assigned to this facility.");
            }
            jdbc.sql("INSERT INTO clinic_sessions (org_id, clinic_id, practitioner_id, weekday, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(t.orgId(), clinicId, s.practitionerId(), s.weekday(), s.startTime(), s.endTime()).update();
        }
    }

    @Transactional(readOnly = true)
    public List<Clinic> clinics(UUID facilityId) {
        TenantContext.Tenant t = TenantContext.require();
        List<UUID> ids = facilityId == null
                ? jdbc.sql("SELECT id FROM clinics WHERE org_id = ? AND facility_id = ANY (?) ORDER BY name").params(t.orgId(), t.facilityIds().toArray(UUID[]::new)).query(UUID.class).list()
                : jdbc.sql("SELECT id FROM clinics WHERE org_id = ? AND facility_id = ? ORDER BY name").params(t.orgId(), facilityId).query(UUID.class).list();
        return ids.stream().map(this::clinic).toList();
    }

    Clinic clinic(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Clinic base = jdbc.sql("SELECT id, facility_id, name, specialty, slot_minutes, active FROM clinics WHERE org_id = ? AND id = ?").params(t.orgId(), id)
                .query((rs, n) -> new Clinic(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getString("name"),
                        rs.getString("specialty"), rs.getInt("slot_minutes"), rs.getBoolean("active"), List.of()))
                .optional().orElseThrow(() -> ApiException.notFound("Clinic"));
        List<Session> sessions = jdbc.sql("SELECT id, practitioner_id, weekday, start_time, end_time FROM clinic_sessions WHERE clinic_id = ? ORDER BY weekday, start_time").param(id)
                .query((rs, n) -> new Session(rs.getObject("id", UUID.class), rs.getObject("practitioner_id", UUID.class), rs.getInt("weekday"),
                        rs.getObject("start_time", java.time.LocalTime.class), rs.getObject("end_time", java.time.LocalTime.class))).list();
        return new Clinic(base.id(), base.facilityId(), base.name(), base.specialty(), base.slotMinutes(), base.active(), sessions);
    }

    // ---- availability and booking ------------------------------------------------------------

    /** Free slots on one day, computed from the weekly sessions minus what is already booked. */
    @Transactional(readOnly = true)
    public List<Slot> slots(UUID clinicId, UUID practitionerId, LocalDate date) {
        Clinic c = clinic(clinicId);
        TenantContext.require().requireFacility(c.facilityId());
        ZoneId zone = zone(c.facilityId());
        List<Slot> free = new ArrayList<>();
        Instant now = Instant.now();
        for (Session s : c.sessions()) {
            if (s.weekday() != date.getDayOfWeek().getValue() || (practitionerId != null && !practitionerId.equals(s.practitionerId()))) {
                continue;
            }
            ZonedDateTime cursor = date.atTime(s.startTime()).atZone(zone);
            ZonedDateTime end = date.atTime(s.endTime()).atZone(zone);
            List<Instant[]> busy = jdbc.sql("""
                    SELECT starts_at, ends_at FROM appointments
                     WHERE org_id = ? AND practitioner_id = ? AND status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
                       AND starts_at < ? AND ends_at > ?""")
                    .params(TenantContext.require().orgId(), s.practitionerId(), java.sql.Timestamp.from(end.toInstant()), java.sql.Timestamp.from(cursor.toInstant()))
                    .query((rs, n) -> new Instant[] {rs.getObject("starts_at", OffsetDateTime.class).toInstant(), rs.getObject("ends_at", OffsetDateTime.class).toInstant()}).list();
            while (!cursor.plusMinutes(c.slotMinutes()).isAfter(end)) {
                Instant from = cursor.toInstant();
                Instant to = cursor.plusMinutes(c.slotMinutes()).toInstant();
                if (from.isAfter(now) && busy.stream().noneMatch(b -> b[0].isBefore(to) && b[1].isAfter(from))) {
                    free.add(new Slot(from, to, s.practitionerId()));
                }
                cursor = cursor.plusMinutes(c.slotMinutes());
            }
        }
        free.sort(java.util.Comparator.comparing(Slot::start));
        return free;
    }

    @Transactional
    public Appointment book(BookInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Clinic c = clinic(in.clinicId());
        t.requireFacility(c.facilityId());
        if (!c.active()) {
            throw ApiException.conflict("clinic_inactive", "That clinic is not taking bookings.");
        }
        patients.requireLive(in.patientId());
        ZoneId zone = zone(c.facilityId());
        ZonedDateTime start = in.startsAt().atZone(zone);
        if (!in.startsAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("in_the_past", "That time has already passed.");
        }
        boolean inSession = c.sessions().stream().anyMatch(s -> s.practitionerId().equals(in.practitionerId())
                && s.weekday() == start.getDayOfWeek().getValue()
                && !start.toLocalTime().isBefore(s.startTime())
                && !start.plusMinutes(c.slotMinutes()).toLocalTime().isAfter(s.endTime())
                && Duration.between(s.startTime(), start.toLocalTime()).toMinutes() % c.slotMinutes() == 0);
        if (!inSession) {
            throw ApiException.badRequest("outside_session", "That is not an available slot for this practitioner.");
        }
        UUID id = insert(t, c, in.patientId(), in.practitionerId(), in.startsAt(), in.startsAt().plus(Duration.ofMinutes(c.slotMinutes())),
                "BOOKED", "ROUTINE", false, in.reason(), null);
        audit.record("appointment.book", "appointment", id, c.facilityId(), null, Map.of("patient", in.patientId().toString()));
        return load(id);
    }

    @Transactional
    public Appointment walkIn(WalkInInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Clinic c = clinic(in.clinicId());
        t.requireFacility(c.facilityId());
        patients.requireLive(in.patientId());
        Instant now = Instant.now();
        UUID id = insert(t, c, in.patientId(), in.practitionerId(), now, now.plus(Duration.ofMinutes(c.slotMinutes())), "CHECKED_IN",
                in.priority() == null ? "ROUTINE" : in.priority(), true, in.reason(), nextQueueNumber(t, c.facilityId(), zone(c.facilityId())));
        jdbc.sql("UPDATE appointments SET checked_in_at = now() WHERE id = ?").param(id).update();
        audit.record("appointment.walk_in", "appointment", id, c.facilityId(), null, Map.of("patient", in.patientId().toString()));
        return load(id);
    }

    private UUID insert(TenantContext.Tenant t, Clinic c, UUID patientId, UUID practitionerId, Instant from, Instant to, String status,
                        String priority, boolean walkIn, String reason, Integer queueNumber) {
        try {
            return jdbc.sql("""
                    INSERT INTO appointments (org_id, facility_id, clinic_id, patient_id, practitioner_id, starts_at, ends_at, status, priority,
                                              walk_in, reason, queue_number, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), c.facilityId(), c.id(), patientId, practitionerId, java.sql.Timestamp.from(from), java.sql.Timestamp.from(to), status,
                            priority, walkIn, blank(reason), queueNumber, t.practitionerId())
                    .query(UUID.class).single();
        } catch (DataIntegrityViolationException e) {
            throw conflict(e);
        }
    }

    @Transactional
    public Appointment reschedule(UUID id, RescheduleInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Appointment a = load(id);
        t.requireFacility(a.facilityId());
        if (!"BOOKED".equals(a.status())) {
            throw ApiException.conflict("not_bookable", "Only a booked appointment can be moved.");
        }
        Clinic c = clinic(a.clinicId());
        if (a.practitionerId() == null || c.sessions().stream().noneMatch(s -> s.practitionerId().equals(a.practitionerId())
                && s.weekday() == in.startsAt().atZone(zone(c.facilityId())).getDayOfWeek().getValue()
                && !in.startsAt().atZone(zone(c.facilityId())).toLocalTime().isBefore(s.startTime())
                && !in.startsAt().atZone(zone(c.facilityId())).plusMinutes(c.slotMinutes()).toLocalTime().isAfter(s.endTime()))) {
            throw ApiException.badRequest("outside_session", "That is not an available slot for this practitioner.");
        }
        int rows;
        try {
            rows = jdbc.sql("UPDATE appointments SET starts_at = ?, ends_at = ?, version = version + 1, updated_at = now() WHERE org_id = ? AND id = ? AND version = ?")
                    .params(java.sql.Timestamp.from(in.startsAt()), java.sql.Timestamp.from(in.startsAt().plus(Duration.ofMinutes(c.slotMinutes()))), t.orgId(), id, in.version()).update();
        } catch (DataIntegrityViolationException e) {
            throw conflict(e);
        }
        if (rows == 0) {
            throw ApiException.conflict("stale_version", "Someone else changed this appointment. Reload it and try again.");
        }
        audit.record("appointment.reschedule", "appointment", id, a.facilityId(), null, Map.of());
        return load(id);
    }

    @Transactional
    public Appointment transition(UUID id, String to, String reason) {
        TenantContext.Tenant t = TenantContext.require();
        Appointment a = load(id);
        t.requireFacility(a.facilityId());
        if (!NEXT.get(a.status()).contains(to)) {
            throw ApiException.conflict("bad_transition", "An appointment that is " + a.status() + " cannot become " + to + ".");
        }
        Integer queue = a.queueNumber();
        if ("CHECKED_IN".equals(to)) {
            ZoneId zone = zone(a.facilityId());
            if (!a.startsAt().atZone(zone).toLocalDate().equals(LocalDate.now(zone))) {
                throw ApiException.conflict("not_today", "Check-in is only possible on the day of the appointment.");
            }
            queue = nextQueueNumber(t, a.facilityId(), zone);
        }
        jdbc.sql("""
                UPDATE appointments SET status = ?, queue_number = ?, cancel_reason = ?, checked_in_at = CASE WHEN ? = 'CHECKED_IN' THEN now() ELSE checked_in_at END,
                       version = version + 1, updated_at = now() WHERE org_id = ? AND id = ?""")
                .params(to, queue, "CANCELLED".equals(to) ? reason : null, to, t.orgId(), id).update();
        audit.record("appointment." + to.toLowerCase(), "appointment", id, a.facilityId(), "CANCELLED".equals(to) ? reason : null, Map.of());
        return load(id);
    }

    @Transactional
    public Appointment setPriority(UUID id, String priority) {
        TenantContext.Tenant t = TenantContext.require();
        Appointment a = load(id);
        t.requireFacility(a.facilityId());
        if (!Set.of("CHECKED_IN", "IN_PROGRESS", "BOOKED").contains(a.status())) {
            throw ApiException.conflict("closed", "That appointment is finished.");
        }
        jdbc.sql("UPDATE appointments SET priority = ?, version = version + 1, updated_at = now() WHERE org_id = ? AND id = ?").params(priority, t.orgId(), id).update();
        audit.record("appointment.priority", "appointment", id, a.facilityId(), null, Map.of("priority", priority));
        return load(id);
    }

    // ---- reading -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Appointment open(UUID id) {
        Appointment a = load(id);
        TenantContext.require().requireFacility(a.facilityId());
        return a;
    }

    /** Who is waiting, most urgent first, then longest waiting. Bounded: a day's queue is one screen. */
    @Transactional(readOnly = true)
    public List<Appointment> queue(UUID facilityId, UUID clinicId) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), facilityId));
        String sql = SELECT + " WHERE a.org_id = ? AND a.facility_id = ? AND a.status IN ('CHECKED_IN', 'IN_PROGRESS')"
                + " AND a.checked_in_at >= date_trunc('day', now() AT TIME ZONE ?) AT TIME ZONE ?";
        p.add(zone(facilityId).getId());
        p.add(zone(facilityId).getId());
        if (clinicId != null) {
            sql += " AND a.clinic_id = ?";
            p.add(clinicId);
        }
        sql += " ORDER BY (a.status = 'IN_PROGRESS') DESC, CASE a.priority WHEN 'EMERGENCY' THEN 0 WHEN 'PRIORITY' THEN 1 ELSE 2 END, a.checked_in_at, a.id LIMIT 200";
        return jdbc.sql(sql).params(p.toArray()).query(SchedulingService::appointment).list();
    }

    @Transactional(readOnly = true)
    public Slice<Appointment> list(UUID facilityId, UUID patientId, String status, LocalDate from, LocalDate to, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT + " WHERE a.org_id = ?");
        p.add(t.orgId());
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND a.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND a.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND a.patient_id = ?");
            p.add(patientId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            p.add(status);
        }
        if (from != null) {
            sql.append(" AND a.starts_at >= ?");
            p.add(java.sql.Timestamp.from(from.atStartOfDay(ZoneId.of("Africa/Nairobi")).toInstant()));
        }
        if (to != null) {
            sql.append(" AND a.starts_at < ?");
            p.add(java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(ZoneId.of("Africa/Nairobi")).toInstant()));
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (a.starts_at, a.id) > (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY a.starts_at, a.id LIMIT ?");
        p.add(size + 1);
        List<Appointment> rows = jdbc.sql(sql.toString()).params(p.toArray()).query(SchedulingService::appointment).list();
        boolean more = rows.size() > size;
        List<Appointment> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).startsAt().toString(), "i", page.get(page.size() - 1).id().toString())) : null;
        return new Slice<>(page, next);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Appointment load(UUID id) {
        return jdbc.sql(SELECT + " WHERE a.org_id = ? AND a.id = ?").params(TenantContext.require().orgId(), id)
                .query(SchedulingService::appointment).optional().orElseThrow(() -> ApiException.notFound("Appointment"));
    }

    private static Appointment appointment(ResultSet rs, int n) throws SQLException {
        return new Appointment(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("clinic_id", UUID.class),
                rs.getObject("patient_id", UUID.class), rs.getString("patient_name"), rs.getObject("practitioner_id", UUID.class),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(), rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                rs.getString("status"), rs.getString("priority"), rs.getBoolean("walk_in"), rs.getString("reason"), (Integer) rs.getObject("queue_number"),
                rs.getObject("checked_in_at", OffsetDateTime.class) == null ? null : rs.getObject("checked_in_at", OffsetDateTime.class).toInstant(),
                rs.getString("cancel_reason"), rs.getInt("version"));
    }

    private ZoneId zone(UUID facilityId) {
        return ZoneId.of(jdbc.sql("SELECT timezone FROM facilities WHERE id = ?").param(facilityId).query(String.class).single());
    }

    /** Queue numbers restart each day, per facility, from an atomic counter. */
    private int nextQueueNumber(TenantContext.Tenant t, UUID facilityId, ZoneId zone) {
        String name = "Q" + LocalDate.now(zone).toString().replace("-", "");
        jdbc.sql("INSERT INTO facility_counters (org_id, facility_id, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").params(t.orgId(), facilityId, name).update();
        return jdbc.sql("UPDATE facility_counters SET next_value = next_value + 1 WHERE facility_id = ? AND name = ? RETURNING next_value - 1")
                .params(facilityId, name).query(Integer.class).single();
    }

    private static ApiException conflict(DataIntegrityViolationException e) {
        String m = String.valueOf(e.getMostSpecificCause().getMessage());
        if (m.contains("appointments_patient_no_overlap")) {
            return ApiException.conflict("patient_double_booked", "That patient already has an appointment at that time.");
        }
        if (m.contains("appointments_no_double_booking")) {
            return ApiException.conflict("slot_taken", "That slot has just been taken.");
        }
        throw e;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
