package com.smartseason.farm.dairy;

import com.smartseason.farm.platform.DomainRuleException;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.FarmRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The herd's numbers for one farm and period.
 *
 * Three decisions worth knowing:
 *
 *   - "Today" is the Nairobi calendar day. Taking it from UTC would make a withdrawal that
 *     ends today look finished from midnight to 03:00 and the dashboard disagree with the
 *     farmer's wall.
 *   - Value is the user's own price per litre times accepted litres. A delivery recorded with
 *     no price contributes nothing and is counted in deliveriesWithoutPrice, so a total is
 *     never silently low.
 *   - Milk recorded from a cow inside a medicine withdrawal window is surfaced, not blocked:
 *     the record is true, and the farmer needs to see that the milk should not have been sold.
 *
 * Every query filters on tenant_id as well as running under row-level security.
 */
@Service
@Transactional(readOnly = true)
public class DairySummaryService {

    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    static final int MAX_DAYS = 366;

    private final FarmRepository farms;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    @org.springframework.beans.factory.annotation.Autowired
    public DairySummaryService(FarmRepository farms) {
        this(farms, Clock.system(NAIROBI));
    }

    DairySummaryService(FarmRepository farms, Clock clock) {
        this.farms = farms;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    public DairySummary summary(UUID farmId, LocalDate from, LocalDate to) {
        UUID tenant = TenantContext.requireTenantId();
        if (!farms.existsByIdAndTenantId(farmId, tenant)) {
            throw new ResourceNotFoundException("Farm", farmId);
        }
        LocalDate end = to != null ? to : today();
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw new DomainRuleException("The start date is after the end date");
        }
        if (start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new DomainRuleException("Ask for at most " + MAX_DAYS + " days at a time");
        }

        List<DairySummary.DayTotal> days = rows(
                "SELECT recorded_on, SUM(litres) FROM milk_yields WHERE tenant_id = ?1 AND farm_id = ?2 "
                        + "AND recorded_on BETWEEN ?3 AND ?4 GROUP BY recorded_on ORDER BY recorded_on",
                tenant, farmId, start, end).stream()
                .map(r -> new DairySummary.DayTotal(((Date) r[0]).toLocalDate(), dec(r[1]))).toList();
        BigDecimal recorded = days.stream().map(DairySummary.DayTotal::litres)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DairySummary.CowTotal> cows = rows(
                "SELECT c.id, c.tag_no, c.name, SUM(y.litres), COUNT(DISTINCT y.recorded_on) "
                        + "FROM milk_yields y JOIN cows c ON c.id = y.cow_id AND c.tenant_id = y.tenant_id "
                        + "WHERE y.tenant_id = ?1 AND y.farm_id = ?2 AND y.recorded_on BETWEEN ?3 AND ?4 "
                        + "GROUP BY c.id, c.tag_no, c.name ORDER BY SUM(y.litres) DESC, c.tag_no",
                tenant, farmId, start, end).stream().map(r -> {
                    BigDecimal litres = dec(r[3]);
                    int n = ((Number) r[4]).intValue();
                    return new DairySummary.CowTotal((UUID) r[0], (String) r[1], (String) r[2], litres, n,
                            n == 0 ? BigDecimal.ZERO : litres.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
                }).toList();

        Object[] d = rows(
                "SELECT COALESCE(SUM(litres_delivered),0), COALESCE(SUM(litres_rejected),0), "
                        + "COALESCE(SUM(fat_pct * litres_delivered) FILTER (WHERE fat_pct IS NOT NULL),0), "
                        + "COALESCE(SUM(litres_delivered) FILTER (WHERE fat_pct IS NOT NULL),0), "
                        + "COALESCE(SUM((litres_delivered - litres_rejected) * price_per_litre) "
                        + "         FILTER (WHERE price_per_litre IS NOT NULL),0), "
                        + "COUNT(*) FILTER (WHERE price_per_litre IS NULL) "
                        + "FROM milk_deliveries WHERE tenant_id = ?1 AND farm_id = ?2 "
                        + "AND delivered_on BETWEEN ?3 AND ?4",
                tenant, farmId, start, end).get(0);
        BigDecimal delivered = dec(d[0]);
        BigDecimal rejected = dec(d[1]);
        BigDecimal fatBase = dec(d[3]);
        BigDecimal fat = fatBase.signum() == 0 ? null
                : dec(d[2]).divide(fatBase, 2, RoundingMode.HALF_UP);
        BigDecimal rate = delivered.signum() == 0 ? BigDecimal.ZERO
                : rejected.multiply(BigDecimal.valueOf(100)).divide(delivered, 2, RoundingMode.HALF_UP);

        int milking = ((Number) scalar(
                "SELECT COUNT(*) FROM cows WHERE tenant_id = ?1 AND farm_id = ?2 AND status = 'MILKING'",
                tenant, farmId)).intValue();

        LocalDate today = today();
        List<DairySummary.Withdrawal> under = rows(
                "SELECT DISTINCT ON (c.id) c.id, c.tag_no, h.medicine, h.withdrawal_ends_on "
                        + "FROM cow_health_events h JOIN cows c ON c.id = h.cow_id AND c.tenant_id = h.tenant_id "
                        + "WHERE h.tenant_id = ?1 AND h.farm_id = ?2 AND h.withdrawal_ends_on >= ?3 "
                        + "AND h.event_date <= ?3 ORDER BY c.id, h.withdrawal_ends_on DESC",
                tenant, farmId, today).stream()
                .map(r -> new DairySummary.Withdrawal((UUID) r[0], (String) r[1], (String) r[2],
                        ((Date) r[3]).toLocalDate()))
                .sorted(java.util.Comparator.comparing(DairySummary.Withdrawal::endsOn)).toList();

        // The event date is the first day milk is held back, the end date the last.
        List<DairySummary.WithdrawalConflict> conflicts = rows(
                "SELECT DISTINCT y.cow_id, c.tag_no, y.recorded_on, y.litres, h.medicine, h.withdrawal_ends_on "
                        + "FROM milk_yields y "
                        + "JOIN cow_health_events h ON h.cow_id = y.cow_id AND h.tenant_id = y.tenant_id "
                        + "  AND h.withdrawal_ends_on IS NOT NULL "
                        + "  AND y.recorded_on BETWEEN h.event_date AND h.withdrawal_ends_on "
                        + "JOIN cows c ON c.id = y.cow_id AND c.tenant_id = y.tenant_id "
                        + "WHERE y.tenant_id = ?1 AND y.farm_id = ?2 AND y.recorded_on BETWEEN ?3 AND ?4 "
                        + "ORDER BY y.recorded_on DESC, c.tag_no",
                tenant, farmId, start, end).stream()
                .map(r -> new DairySummary.WithdrawalConflict((UUID) r[0], (String) r[1],
                        ((Date) r[2]).toLocalDate(), dec(r[3]), (String) r[4], ((Date) r[5]).toLocalDate()))
                .toList();

        List<DairySummary.ExpectedCalving> calvings = rows(
                "SELECT DISTINCT ON (b.cow_id) b.cow_id, c.tag_no, b.expected_calving_on "
                        + "FROM breeding_events b JOIN cows c ON c.id = b.cow_id AND c.tenant_id = b.tenant_id "
                        + "WHERE b.tenant_id = ?1 AND b.farm_id = ?2 AND b.expected_calving_on >= ?3 "
                        + "AND c.status NOT IN ('SOLD','DEAD') "
                        + "AND NOT EXISTS (SELECT 1 FROM breeding_events k WHERE k.tenant_id = b.tenant_id "
                        + "  AND k.cow_id = b.cow_id AND k.event_type = 'CALVING' AND k.event_date >= b.event_date) "
                        + "ORDER BY b.cow_id, b.event_date DESC",
                tenant, farmId, today).stream()
                .map(r -> new DairySummary.ExpectedCalving((UUID) r[0], (String) r[1], ((Date) r[2]).toLocalDate()))
                .sorted(java.util.Comparator.comparing(DairySummary.ExpectedCalving::expectedOn)).toList();

        return new DairySummary(farmId, start, end, recorded, delivered, rejected, rate, fat,
                dec(d[4]), ((Number) d[5]).intValue(), milking, days, cows, under, conflicts, calvings);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Object... params) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            query.setParameter(i + 1, p instanceof LocalDate date ? Date.valueOf(date) : p);
        }
        return (List<Object[]>) query.getResultList();
    }

    private Object scalar(String sql, Object... params) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return query.getSingleResult();
    }

    private static BigDecimal dec(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
