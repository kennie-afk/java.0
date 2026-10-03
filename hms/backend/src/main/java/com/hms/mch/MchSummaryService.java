package com.hms.mch;

import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts for the maternal and child health programme at one facility over a period. Aggregates only: no names,
 * so it carries no patient identity. These are the service's own counts, not the Ministry of Health's official
 * returns, whose indicator definitions this does not claim to reproduce.
 */
@Service
public class MchSummaryService {

    private static final int MAX_DAYS = 366;

    private final JdbcClient jdbc;

    public MchSummaryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID facilityId, LocalDate from, LocalDate to) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("bad_period", "The start date is after the end date.");
        }
        if (start.plusDays(MAX_DAYS).isBefore(end)) {
            throw ApiException.badRequest("period_too_long", "Ask for at most a year at a time.");
        }
        UUID org = t.orgId();

        Map<String, Object> pregnancies = jdbc.sql("""
                SELECT count(*) AS active,
                       count(*) FILTER (WHERE lv.next_visit_on < current_date) AS overdue,
                       count(*) FILTER (WHERE lv.risk_flags && ARRAY['SEVERE_HYPERTENSION','PRE_ECLAMPSIA_SIGNS','SEVERE_ANAEMIA','FETAL_HEART_RATE']) AS danger
                FROM pregnancies pr
                LEFT JOIN LATERAL (SELECT v.next_visit_on, v.risk_flags FROM anc_visits v
                                    WHERE v.org_id = pr.org_id AND v.pregnancy_id = pr.id
                                    ORDER BY v.visit_number DESC LIMIT 1) lv ON true
                WHERE pr.org_id = ? AND pr.facility_id = ? AND pr.status = 'ACTIVE'""")
                .params(org, facilityId).query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("active", rs.getLong("active"));
                    m.put("overdueForVisit", rs.getLong("overdue"));
                    m.put("latestVisitHasDangerSign", rs.getLong("danger"));
                    return m;
                }).single();

        Map<String, Object> anc = jdbc.sql("""
                SELECT count(*) AS visits,
                       count(*) FILTER (WHERE visit_number = 1) AS first_visits,
                       count(*) FILTER (WHERE iptp_given) AS iptp,
                       count(*) FILTER (WHERE iron_folate_given) AS iron,
                       count(*) FILTER (WHERE hiv_status IS NOT NULL AND hiv_status <> 'NOT_TESTED') AS hiv_tested,
                       count(*) FILTER (WHERE hiv_status = 'POSITIVE') AS hiv_new_positive
                FROM anc_visits WHERE org_id = ? AND facility_id = ? AND visited_on BETWEEN ? AND ?""")
                .params(org, facilityId, start, end).query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("visits", rs.getLong("visits"));
                    m.put("firstVisits", rs.getLong("first_visits"));
                    m.put("iptpGiven", rs.getLong("iptp"));
                    m.put("ironFolateGiven", rs.getLong("iron"));
                    m.put("hivTested", rs.getLong("hiv_tested"));
                    m.put("hivNewPositive", rs.getLong("hiv_new_positive"));
                    return m;
                }).single();

        Map<String, Long> byMode = new LinkedHashMap<>();
        jdbc.sql("SELECT mode, count(*) AS c FROM deliveries WHERE org_id = ? AND facility_id = ? AND delivered_on BETWEEN ? AND ? GROUP BY mode ORDER BY mode")
                .params(org, facilityId, start, end).query((rs, n) -> byMode.put(rs.getString("mode"), rs.getLong("c"))).list();
        Map<String, Object> deliveries = jdbc.sql("""
                SELECT count(*) AS total,
                       coalesce(sum(babies) FILTER (WHERE outcome = 'LIVE_BIRTH'), 0) AS live_births,
                       count(*) FILTER (WHERE outcome = 'STILLBIRTH') AS stillbirths,
                       count(*) FILTER (WHERE outcome = 'MISCARRIAGE') AS miscarriages,
                       count(*) FILTER (WHERE birth_weight_g < 2500) AS low_birth_weight
                FROM deliveries WHERE org_id = ? AND facility_id = ? AND delivered_on BETWEEN ? AND ?""")
                .params(org, facilityId, start, end).query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("total", rs.getLong("total"));
                    m.put("liveBirths", rs.getLong("live_births"));
                    m.put("stillbirths", rs.getLong("stillbirths"));
                    m.put("miscarriages", rs.getLong("miscarriages"));
                    m.put("lowBirthWeight", rs.getLong("low_birth_weight"));
                    m.put("byMode", byMode);
                    return m;
                }).single();

        Map<String, Long> byVaccine = new LinkedHashMap<>();
        jdbc.sql("SELECT vaccine, count(*) AS c FROM immunisations WHERE org_id = ? AND facility_id = ? AND given_on BETWEEN ? AND ? GROUP BY vaccine ORDER BY vaccine")
                .params(org, facilityId, start, end).query((rs, n) -> byVaccine.put(rs.getString("vaccine"), rs.getLong("c"))).list();
        long doses = byVaccine.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("facilityId", facilityId);
        out.put("from", start);
        out.put("to", end);
        out.put("pregnancies", pregnancies);
        out.put("antenatal", anc);
        out.put("deliveries", deliveries);
        out.put("immunisation", Map.of("dosesGiven", doses, "byVaccine", byVaccine));
        return out;
    }
}
