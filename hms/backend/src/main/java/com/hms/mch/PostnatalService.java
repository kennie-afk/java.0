package com.hms.mch;

import static com.hms.mch.MchModels.Flag;
import static com.hms.mch.PostnatalModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.registry.PatientAccess;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Care of the mother and baby after a pregnancy has been closed with an outcome.
 *
 * <p>As with antenatal visits, the warning flags are worked out here from the readings and never supplied by the client.
 * They prompt a clinician and do not replace one. The thresholds are common clinical cut-offs, not a Ministry of Health protocol.
 */
@Service
public class PostnatalService {

    /** The postnatal period is six weeks; two more are allowed for a late first contact before the entry is refused as a probable date error. */
    static final int MAX_DAYS_SINCE_DELIVERY = 56;
    static final String SCHEDULE_NOTE = "WHO advises postnatal contacts in the first 24 hours, around day 3, between days 7 and 14, and at 6 weeks.";

    private static final Map<String, Flag> FLAGS = Map.ofEntries(
            Map.entry("SEVERE_HYPERTENSION", new Flag("SEVERE_HYPERTENSION", "DANGER", "Blood pressure is in the severe range. Refer urgently.")),
            Map.entry("HYPERTENSION", new Flag("HYPERTENSION", "WARNING", "Blood pressure is 140/90 or higher. Recheck and assess for postpartum pre-eclampsia.")),
            Map.entry("MATERNAL_FEVER", new Flag("MATERNAL_FEVER", "DANGER", "Temperature is 38.0 C or higher. Look for infection.")),
            Map.entry("HEAVY_LOCHIA", new Flag("HEAVY_LOCHIA", "DANGER", "Heavy lochia. Assess for postpartum haemorrhage.")),
            Map.entry("OFFENSIVE_LOCHIA", new Flag("OFFENSIVE_LOCHIA", "WARNING", "Offensive lochia. Look for infection.")),
            Map.entry("SUBINVOLUTION", new Flag("SUBINVOLUTION", "WARNING", "The uterus is not involuting as expected.")),
            Map.entry("WOUND_INFECTION", new Flag("WOUND_INFECTION", "WARNING", "The perineal or caesarean wound looks infected.")),
            Map.entry("LOW_MOOD", new Flag("LOW_MOOD", "WARNING", "Low mood reported. Screen for postnatal depression.")),
            Map.entry("NEWBORN_FEVER", new Flag("NEWBORN_FEVER", "DANGER", "Baby's temperature is 37.5 C or higher.")),
            Map.entry("NEWBORN_COLD", new Flag("NEWBORN_COLD", "DANGER", "Baby's temperature is below 36.5 C.")),
            Map.entry("CORD_INFECTION", new Flag("CORD_INFECTION", "DANGER", "The cord looks infected.")),
            Map.entry("JAUNDICE", new Flag("JAUNDICE", "WARNING", "Baby is jaundiced. Assess the level and timing.")),
            Map.entry("POOR_FEEDING", new Flag("POOR_FEEDING", "WARNING", "Baby is not feeding well.")));

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;

    public PostnatalService(JdbcClient jdbc, AuditService audit, PatientAccess patients) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
    }

    private record Closed(UUID facilityId, UUID patientId, String patientName, LocalDate deliveredOn, String outcome, int babies) {}

    /** The closed pregnancy with its outcome, optionally under a row lock so two clerks cannot take the same visit number. */
    private Closed closed(UUID pregnancyId, boolean lock) {
        TenantContext.Tenant t = TenantContext.require();
        var row = jdbc.sql("""
                SELECT pr.facility_id, pr.patient_id, pr.status, pt.given_name || ' ' || pt.family_name AS name,
                       d.delivered_on, d.outcome, d.babies
                  FROM pregnancies pr
                  JOIN patients pt ON pt.org_id = pr.org_id AND pt.id = pr.patient_id
                  LEFT JOIN deliveries d ON d.org_id = pr.org_id AND d.pregnancy_id = pr.id
                 WHERE pr.org_id = ? AND pr.id = ?""" + (lock ? " FOR UPDATE OF pr" : ""))
                .params(t.orgId(), pregnancyId)
                .query((rs, n) -> new Object[] {rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("status"),
                        rs.getString("name"), rs.getObject("delivered_on", LocalDate.class), rs.getString("outcome"), rs.getInt("babies")})
                .optional().orElseThrow(() -> ApiException.notFound("Pregnancy"));
        patients.require((UUID) row[1]);
        t.requireFacility((UUID) row[0]);
        if ("ACTIVE".equals(row[2]) || row[4] == null) {
            throw ApiException.conflict("pregnancy_open", "Postnatal care starts once the pregnancy has an outcome. Record the delivery first.");
        }
        return new Closed((UUID) row[0], (UUID) row[1], (String) row[3], (LocalDate) row[4], (String) row[5], (Integer) row[6]);
    }

    @Transactional(readOnly = true)
    public Postnatal get(UUID pregnancyId) {
        TenantContext.Tenant t = TenantContext.require();
        Closed c = closed(pregnancyId, false);
        List<PostnatalVisit> visits = jdbc.sql("SELECT * FROM postnatal_visits WHERE org_id = ? AND pregnancy_id = ? ORDER BY visit_number").params(t.orgId(), pregnancyId)
                .query(PostnatalService::visitRow).list();
        LocalDate next = visits.isEmpty() ? null : visits.get(visits.size() - 1).nextVisitOn();
        return new Postnatal(pregnancyId, c.patientId(), c.patientName(), c.deliveredOn(), c.outcome(), c.babies(),
                (int) ChronoUnit.DAYS.between(c.deliveredOn(), LocalDate.now()), "LIVE_BIRTH".equals(c.outcome()), next,
                next != null && next.isBefore(LocalDate.now()), SCHEDULE_NOTE, visits);
    }

    @Transactional
    public Postnatal addVisit(UUID pregnancyId, PostnatalVisitInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Closed c = closed(pregnancyId, true);
        patients.requireAlive(c.patientId());
        LocalDate visitedOn = in.visitedOn() == null ? LocalDate.now() : in.visitedOn();
        if (visitedOn.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("visit_in_future", "A visit cannot be dated in the future.");
        }
        int days = (int) ChronoUnit.DAYS.between(c.deliveredOn(), visitedOn);
        if (days < 0) {
            throw ApiException.badRequest("visit_before_delivery", "The visit is dated before the delivery (" + c.deliveredOn() + ").");
        }
        if (days > MAX_DAYS_SINCE_DELIVERY) {
            throw ApiException.badRequest("outside_postnatal_period", "That visit is more than " + MAX_DAYS_SINCE_DELIVERY + " days after the delivery, which is outside the postnatal period.");
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
        boolean babyFields = in.babyWeightG() != null || in.babyTemperatureC() != null || in.cord() != null || in.jaundice() != null || in.feedingWell() != null;
        if (babyFields && !"LIVE_BIRTH".equals(c.outcome())) {
            throw ApiException.badRequest("no_live_baby", "Baby readings apply only after a live birth.");
        }
        var last = jdbc.sql("SELECT visit_number, visited_on FROM postnatal_visits WHERE org_id = ? AND pregnancy_id = ? ORDER BY visit_number DESC LIMIT 1")
                .params(t.orgId(), pregnancyId).query((rs, n) -> new Object[] {rs.getInt("visit_number"), rs.getObject("visited_on", LocalDate.class)}).optional();
        if (last.isPresent() && visitedOn.isBefore((LocalDate) last.get()[1])) {
            throw ApiException.conflict("visit_out_of_order", "Visits are entered in date order. The last one was on " + last.get()[1] + ".");
        }
        int number = last.map(o -> (Integer) o[0] + 1).orElse(1);
        List<String> flags = flagsFor(in);
        jdbc.sql("""
                INSERT INTO postnatal_visits (org_id, pregnancy_id, facility_id, visit_number, visited_on, days_since_delivery, systolic, diastolic,
                  temperature_c, uterus, lochia, wound, breastfeeding, low_mood, fp_counselled, baby_weight_g, baby_temperature_c, cord, jaundice,
                  feeding_well, risk_flags, notes, next_visit_on, recorded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(t.orgId(), pregnancyId, c.facilityId(), number, visitedOn, days, in.systolic(), in.diastolic(), in.temperatureC(), in.uterus(), in.lochia(),
                        in.wound(), in.breastfeeding(), Boolean.TRUE.equals(in.lowMood()), Boolean.TRUE.equals(in.fpCounselled()), in.babyWeightG(),
                        in.babyTemperatureC(), in.cord(), in.jaundice(), in.feedingWell(), flags.toArray(String[]::new),
                        in.notes() == null || in.notes().isBlank() ? null : in.notes().trim(), in.nextVisitOn(), t.practitionerId())
                .update();
        audit.record("postnatal.visit", "pregnancy", pregnancyId, c.facilityId(), null, Map.of("visit", number, "flags", flags));
        return get(pregnancyId);
    }

    static List<String> flagsFor(PostnatalVisitInput in) {
        List<String> out = new ArrayList<>();
        if (in.systolic() != null) {
            if (in.systolic() >= 160 || in.diastolic() >= 110) {
                out.add("SEVERE_HYPERTENSION");
            } else if (in.systolic() >= 140 || in.diastolic() >= 90) {
                out.add("HYPERTENSION");
            }
        }
        if (in.temperatureC() != null && in.temperatureC().doubleValue() >= 38.0) {
            out.add("MATERNAL_FEVER");
        }
        if ("HEAVY".equals(in.lochia())) {
            out.add("HEAVY_LOCHIA");
        }
        if ("OFFENSIVE".equals(in.lochia())) {
            out.add("OFFENSIVE_LOCHIA");
        }
        if ("SUBINVOLUTED".equals(in.uterus())) {
            out.add("SUBINVOLUTION");
        }
        if ("INFECTED".equals(in.wound())) {
            out.add("WOUND_INFECTION");
        }
        if (Boolean.TRUE.equals(in.lowMood())) {
            out.add("LOW_MOOD");
        }
        if (in.babyTemperatureC() != null) {
            if (in.babyTemperatureC().doubleValue() >= 37.5) {
                out.add("NEWBORN_FEVER");
            } else if (in.babyTemperatureC().doubleValue() < 36.5) {
                out.add("NEWBORN_COLD");
            }
        }
        if ("INFECTED".equals(in.cord())) {
            out.add("CORD_INFECTION");
        }
        if (Boolean.TRUE.equals(in.jaundice())) {
            out.add("JAUNDICE");
        }
        if (Boolean.FALSE.equals(in.feedingWell())) {
            out.add("POOR_FEEDING");
        }
        return out;
    }

    private static PostnatalVisit visitRow(ResultSet rs, int n) throws SQLException {
        return new PostnatalVisit(rs.getObject("id", UUID.class), rs.getInt("visit_number"), rs.getObject("visited_on", LocalDate.class), rs.getInt("days_since_delivery"),
                (Integer) rs.getObject("systolic"), (Integer) rs.getObject("diastolic"), rs.getBigDecimal("temperature_c"), rs.getString("uterus"), rs.getString("lochia"),
                rs.getString("wound"), rs.getString("breastfeeding"), rs.getBoolean("low_mood"), rs.getBoolean("fp_counselled"), (Integer) rs.getObject("baby_weight_g"),
                rs.getBigDecimal("baby_temperature_c"), rs.getString("cord"), (Boolean) rs.getObject("jaundice"), (Boolean) rs.getObject("feeding_well"),
                flagList(rs.getArray("risk_flags")), rs.getString("notes"), rs.getObject("next_visit_on", LocalDate.class));
    }

    private static List<Flag> flagList(java.sql.Array array) throws SQLException {
        List<Flag> out = new ArrayList<>();
        if (array != null) {
            for (Object code : (Object[]) array.getArray()) {
                Flag f = FLAGS.get(String.valueOf(code));
                if (f != null) {
                    out.add(f);
                }
            }
        }
        return out;
    }
}
