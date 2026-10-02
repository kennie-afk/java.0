package com.hms.reporting;

import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate reports for one facility over a date range (in the facility's own time zone). These are
 * counts and sums only: no patient is named. They are operational reports, NOT the official MOH
 * returns (705, 711, 731...), whose exact layouts and definitions have not been reproduced here.
 */
@Service
public class ReportingService {

    static final int MAX_DAYS = 366;

    public record Count(String key, long count) {}

    public record Period(UUID facilityId, LocalDate from, LocalDate to, String timezone) {}

    public record Outpatient(Period period, long visits, long uniquePatients, long under5, long fiveAndOver, List<Count> bySex, List<Count> byAgeBand, List<Count> byType,
                             List<Count> topDiagnoses, String note) {}

    public record Finance(Period period, BigDecimal invoiced, BigDecimal collected, BigDecimal reversed, BigDecimal outstanding, long invoices, List<MoneyLine> collectedByMethod,
                          List<MoneyLine> invoicedByPayer) {}

    public record MoneyLine(String key, BigDecimal amount, long count) {}

    public record Inpatient(Period period, long admissions, long discharges, Double averageStayDays, List<Count> dischargesByOutcome, long bedsTotal, long bedsOccupied,
                            double occupancyPercent) {}

    public record Laboratory(Period period, long orders, long tests, long validated, Double averageTurnaroundMinutes, List<Count> byFlag, long unacknowledgedCritical) {}

    public record Overview(Outpatient outpatient, Finance finance, Inpatient inpatient, Laboratory laboratory) {}

    private final JdbcClient jdbc;

    public ReportingService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Range(Period period, Timestamp start, Timestamp end) {}

    private Range range(UUID facilityId, LocalDate from, LocalDate to) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("bad_range", "Give a from and to date, with to on or after from.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw ApiException.badRequest("range_too_long", "A report covers at most " + MAX_DAYS + " days.");
        }
        String tz = jdbc.sql("SELECT timezone FROM facilities WHERE org_id = ? AND id = ?").params(t.orgId(), facilityId).query(String.class).optional()
                .orElseThrow(() -> ApiException.notFound("Facility"));
        ZoneId zone = ZoneId.of(tz);
        return new Range(new Period(facilityId, from, to, tz), Timestamp.from(from.atStartOfDay(zone).toInstant()), Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID facilityId, LocalDate from, LocalDate to) {
        return new Overview(outpatient(facilityId, from, to), finance(facilityId, from, to), inpatient(facilityId, from, to), laboratory(facilityId, from, to));
    }

    @Transactional(readOnly = true)
    public Outpatient outpatient(UUID facilityId, LocalDate from, LocalDate to) {
        Range r = range(facilityId, from, to);
        UUID org = TenantContext.require().orgId();
        // Outpatient and emergency encounters that started in the period, counted once each.
        Map<String, Object> totals = jdbc.sql("""
                SELECT count(*) AS visits, count(DISTINCT e.patient_id) AS people,
                       count(*) FILTER (WHERE age(e.started_at, p.birth_date) < interval '5 years') AS under5
                  FROM encounters e JOIN patients p ON p.org_id = e.org_id AND p.id = e.patient_id
                 WHERE e.org_id = ? AND e.facility_id = ? AND e.encounter_type IN ('OPD', 'ED') AND e.started_at >= ? AND e.started_at < ?""")
                .params(org, facilityId, r.start, r.end).query().singleRow();
        long visits = ((Number) totals.get("visits")).longValue();
        long under5 = ((Number) totals.get("under5")).longValue();
        List<Count> bySex = jdbc.sql("""
                SELECT p.sex AS k, count(*) AS n FROM encounters e JOIN patients p ON p.org_id = e.org_id AND p.id = e.patient_id
                 WHERE e.org_id = ? AND e.facility_id = ? AND e.encounter_type IN ('OPD', 'ED') AND e.started_at >= ? AND e.started_at < ? GROUP BY p.sex ORDER BY p.sex""")
                .params(org, facilityId, r.start, r.end).query((rs, i) -> new Count(rs.getString("k"), rs.getLong("n"))).list();
        List<Count> byAge = jdbc.sql("""
                SELECT band AS k, count(*) AS n FROM (
                  SELECT CASE WHEN a < 5 THEN '0-4' WHEN a < 18 THEN '5-17' WHEN a < 60 THEN '18-59' ELSE '60+' END AS band
                    FROM (SELECT extract(year FROM age(e.started_at, p.birth_date))::int AS a FROM encounters e JOIN patients p ON p.org_id = e.org_id AND p.id = e.patient_id
                           WHERE e.org_id = ? AND e.facility_id = ? AND e.encounter_type IN ('OPD', 'ED') AND e.started_at >= ? AND e.started_at < ?) x) y
                 GROUP BY band ORDER BY min(CASE band WHEN '0-4' THEN 1 WHEN '5-17' THEN 2 WHEN '18-59' THEN 3 ELSE 4 END)""")
                .params(org, facilityId, r.start, r.end).query((rs, i) -> new Count(rs.getString("k"), rs.getLong("n"))).list();
        List<Count> byType = jdbc.sql("""
                SELECT encounter_type AS k, count(*) AS n FROM encounters WHERE org_id = ? AND facility_id = ? AND encounter_type IN ('OPD', 'ED') AND started_at >= ? AND started_at < ?
                 GROUP BY encounter_type ORDER BY encounter_type""").params(org, facilityId, r.start, r.end).query((rs, i) -> new Count(rs.getString("k"), rs.getLong("n"))).list();
        List<Count> top = jdbc.sql("""
                SELECT d.icd11_code || ' ' || min(d.title) AS k, count(DISTINCT d.encounter_id) AS n FROM diagnoses d JOIN encounters e ON e.org_id = d.org_id AND e.id = d.encounter_id
                 WHERE d.org_id = ? AND d.facility_id = ? AND d.certainty <> 'RULED_OUT' AND e.started_at >= ? AND e.started_at < ? GROUP BY d.icd11_code ORDER BY n DESC, d.icd11_code LIMIT 10""")
                .params(org, facilityId, r.start, r.end).query((rs, i) -> new Count(rs.getString("k"), rs.getLong("n"))).list();
        return new Outpatient(r.period, visits, ((Number) totals.get("people")).longValue(), under5, visits - under5, bySex, byAge, byType, top,
                "Operational counts. Not the official MOH 705 layout; its exact definitions are not reproduced here.");
    }

    @Transactional(readOnly = true)
    public Finance finance(UUID facilityId, LocalDate from, LocalDate to) {
        Range r = range(facilityId, from, to);
        UUID org = TenantContext.require().orgId();
        var inv = jdbc.sql("""
                SELECT coalesce(sum(total), 0) AS invoiced, coalesce(sum(total - amount_paid), 0) AS outstanding, count(*) AS n FROM invoices
                 WHERE org_id = ? AND facility_id = ? AND status <> 'DRAFT' AND status <> 'VOID' AND issued_at >= ? AND issued_at < ?""").params(org, facilityId, r.start, r.end).query().singleRow();
        var pay = jdbc.sql("""
                SELECT coalesce(sum(amount) FILTER (WHERE status = 'COMPLETED'), 0) AS collected, coalesce(sum(amount) FILTER (WHERE status = 'REVERSED'), 0) AS reversed
                  FROM payments WHERE org_id = ? AND facility_id = ? AND completed_at >= ? AND completed_at < ?""").params(org, facilityId, r.start, r.end).query().singleRow();
        List<MoneyLine> byMethod = jdbc.sql("""
                SELECT method AS k, sum(amount) AS a, count(*) AS n FROM payments WHERE org_id = ? AND facility_id = ? AND status = 'COMPLETED' AND completed_at >= ? AND completed_at < ?
                 GROUP BY method ORDER BY method""").params(org, facilityId, r.start, r.end).query((rs, i) -> new MoneyLine(rs.getString("k"), rs.getBigDecimal("a"), rs.getLong("n"))).list();
        List<MoneyLine> byPayer = jdbc.sql("""
                SELECT payer_type AS k, sum(total) AS a, count(*) AS n FROM invoices WHERE org_id = ? AND facility_id = ? AND status NOT IN ('DRAFT', 'VOID') AND issued_at >= ? AND issued_at < ?
                 GROUP BY payer_type ORDER BY payer_type""").params(org, facilityId, r.start, r.end).query((rs, i) -> new MoneyLine(rs.getString("k"), rs.getBigDecimal("a"), rs.getLong("n"))).list();
        return new Finance(r.period, (BigDecimal) inv.get("invoiced"), (BigDecimal) pay.get("collected"), (BigDecimal) pay.get("reversed"), (BigDecimal) inv.get("outstanding"),
                ((Number) inv.get("n")).longValue(), byMethod, byPayer);
    }

    @Transactional(readOnly = true)
    public Inpatient inpatient(UUID facilityId, LocalDate from, LocalDate to) {
        Range r = range(facilityId, from, to);
        UUID org = TenantContext.require().orgId();
        long admissions = jdbc.sql("SELECT count(*) FROM admissions WHERE org_id = ? AND facility_id = ? AND admitted_at >= ? AND admitted_at < ?").params(org, facilityId, r.start, r.end)
                .query(Long.class).single();
        var dis = jdbc.sql("""
                SELECT count(*) AS n, avg(extract(epoch FROM (discharged_at - admitted_at)) / 86400.0) AS los FROM admissions
                 WHERE org_id = ? AND facility_id = ? AND status = 'DISCHARGED' AND discharged_at >= ? AND discharged_at < ?""").params(org, facilityId, r.start, r.end).query().singleRow();
        List<Count> outcomes = jdbc.sql("""
                SELECT discharge_type AS k, count(*) AS n FROM admissions WHERE org_id = ? AND facility_id = ? AND status = 'DISCHARGED' AND discharged_at >= ? AND discharged_at < ?
                 GROUP BY discharge_type ORDER BY discharge_type""").params(org, facilityId, r.start, r.end).query((rs, i) -> new Count(rs.getString("k"), rs.getLong("n"))).list();
        var beds = jdbc.sql("""
                SELECT count(*) FILTER (WHERE status <> 'OUT_OF_SERVICE') AS total, count(*) FILTER (WHERE status = 'OCCUPIED') AS occupied FROM beds WHERE org_id = ? AND facility_id = ?""")
                .params(org, facilityId).query().singleRow();
        long total = ((Number) beds.get("total")).longValue();
        long occupied = ((Number) beds.get("occupied")).longValue();
        Number los = (Number) dis.get("los");
        return new Inpatient(r.period, admissions, ((Number) dis.get("n")).longValue(), los == null ? null : Math.round(los.doubleValue() * 10) / 10.0, outcomes, total, occupied,
                total == 0 ? 0 : Math.round(occupied * 1000.0 / total) / 10.0);
    }

    @Transactional(readOnly = true)
    public Laboratory laboratory(UUID facilityId, LocalDate from, LocalDate to) {
        Range r = range(facilityId, from, to);
        UUID org = TenantContext.require().orgId();
        long orders = jdbc.sql("SELECT count(*) FROM lab_orders WHERE org_id = ? AND facility_id = ? AND status <> 'CANCELLED' AND created_at >= ? AND created_at < ?").params(org, facilityId, r.start, r.end)
                .query(Long.class).single();
        var items = jdbc.sql("""
                SELECT count(*) AS tests, count(*) FILTER (WHERE i.status = 'VALIDATED') AS validated,
                       avg(extract(epoch FROM (i.validated_at - o.created_at)) / 60.0) FILTER (WHERE i.status = 'VALIDATED') AS tat
                  FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id
                 WHERE i.org_id = ? AND o.facility_id = ? AND i.status <> 'CANCELLED' AND o.created_at >= ? AND o.created_at < ?""").params(org, facilityId, r.start, r.end).query().singleRow();
        List<Count> flags = jdbc.sql("""
                SELECT i.flag AS k, count(*) AS n FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id
                 WHERE i.org_id = ? AND o.facility_id = ? AND i.flag IS NOT NULL AND i.status = 'VALIDATED' AND o.created_at >= ? AND o.created_at < ? GROUP BY i.flag ORDER BY i.flag""")
                .params(org, facilityId, r.start, r.end).query((rs, i) -> new Count(rs.getString("k"), rs.getLong("n"))).list();
        long critical = jdbc.sql("""
                SELECT count(*) FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id
                 WHERE i.org_id = ? AND o.facility_id = ? AND i.critical AND i.critical_ack_at IS NULL""").params(org, facilityId).query(Long.class).single();
        Number tat = (Number) items.get("tat");
        return new Laboratory(r.period, orders, ((Number) items.get("tests")).longValue(), ((Number) items.get("validated")).longValue(), tat == null ? null : Math.round(tat.doubleValue() * 10) / 10.0,
                flags, critical);
    }
}
