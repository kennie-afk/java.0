package com.smartseason.farm.dairy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartseason.farm.platform.DomainRuleException;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the real migrations (V1, V3, V4) on a real Postgres and checks the dairy summary and the
 * constraints against it. The summary uses DISTINCT ON and FILTER, which H2 does not have, so a
 * mock would prove nothing. Skipped unless DAIRY_TEST_PG_URL is set, e.g.
 *   DAIRY_TEST_PG_URL=jdbc:postgresql://localhost:55452/farm_dairy_test
 *   DAIRY_TEST_PG_USER=postgres DAIRY_TEST_PG_PASSWORD=...
 */
@EnabledIfEnvironmentVariable(named = "DAIRY_TEST_PG_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${DAIRY_TEST_PG_URL}",
        "spring.datasource.username=${DAIRY_TEST_PG_USER:postgres}",
        "spring.datasource.password=${DAIRY_TEST_PG_PASSWORD:postgres}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=false"})
class DairySummaryPostgresTest {

    @Autowired private DairySummaryService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.flywaydb.core.Flyway flyway;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private UUID farmA;
    private UUID farmB;
    private UUID cow1;
    private UUID cow2;
    private final LocalDate today = LocalDate.now(DairySummaryService.NAIROBI);

    @BeforeEach
    void seed() {
        farmA = farm(tenantA);
        farmB = farm(tenantB);
        cow1 = cow(tenantA, farmA, "A001", "MILKING");
        cow2 = cow(tenantA, farmA, "A002", "MILKING");
        cow(tenantB, farmB, "A001", "MILKING");
        TenantContext.set(tenantA);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        for (String t : new String[]{"breeding_events", "cow_health_events", "milk_deliveries", "milk_yields",
                "cows", "farms"}) {
            jdbc.update("DELETE FROM " + t + " WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        }
    }

    @Test
    void totals_per_day_and_per_cow_exclude_other_tenants_and_other_dates() {
        milk(tenantA, farmA, cow1, today.minusDays(1), "MORNING", "10.5");
        milk(tenantA, farmA, cow1, today.minusDays(1), "EVENING", "8.0");
        milk(tenantA, farmA, cow2, today.minusDays(1), "MORNING", "12");
        milk(tenantA, farmA, cow2, today.minusDays(60), "MORNING", "99");
        UUID otherCow = cow(tenantB, farmB, "B9", "MILKING");
        milk(tenantB, farmB, otherCow, today.minusDays(1), "MORNING", "50");

        DairySummary s = service.summary(farmA, today.minusDays(29), today);

        assertThat(s.litresRecorded()).isEqualByComparingTo("30.5");
        assertThat(s.days()).hasSize(1);
        assertThat(s.cows()).extracting(DairySummary.CowTotal::tagNo).containsExactly("A001", "A002");
        assertThat(s.cows().get(0).litresPerRecordedDay()).isEqualByComparingTo("18.50");
        assertThat(s.milkingCows()).isEqualTo(2);
    }

    @Test
    void deliveries_value_uses_accepted_litres_and_counts_unpriced_ones() {
        delivery(tenantA, farmA, today.minusDays(2), "100", "10", "4.0", "50");
        delivery(tenantA, farmA, today.minusDays(1), "100", "0", "3.0", null);

        DairySummary s = service.summary(farmA, today.minusDays(29), today);

        assertThat(s.litresDelivered()).isEqualByComparingTo("200");
        assertThat(s.litresRejected()).isEqualByComparingTo("10");
        assertThat(s.rejectionRatePct()).isEqualByComparingTo("5.00");
        assertThat(s.weightedFatPct()).isEqualByComparingTo("3.50");
        assertThat(s.deliveredValue()).isEqualByComparingTo("4500");
        assertThat(s.deliveriesWithoutPrice()).isEqualTo(1);
    }

    @Test
    void withdrawal_is_flagged_for_cows_and_for_milk_recorded_inside_it() {
        jdbc.update("INSERT INTO cow_health_events (id, tenant_id, cow_id, farm_id, event_date, event_type, medicine, "
                + "withdrawal_ends_on) VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), tenantA, cow1, farmA,
                today.minusDays(3), "TREATMENT", "Oxytetracycline", today.plusDays(2));
        milk(tenantA, farmA, cow1, today.minusDays(1), "MORNING", "9");
        milk(tenantA, farmA, cow1, today.minusDays(10), "MORNING", "9");
        milk(tenantA, farmA, cow2, today.minusDays(1), "MORNING", "9");

        DairySummary s = service.summary(farmA, today.minusDays(29), today);

        assertThat(s.underWithdrawal()).singleElement().satisfies(w -> {
            assertThat(w.tagNo()).isEqualTo("A001");
            assertThat(w.endsOn()).isEqualTo(today.plusDays(2));
        });
        assertThat(s.milkedDuringWithdrawal()).singleElement().satisfies(c -> {
            assertThat(c.tagNo()).isEqualTo("A001");
            assertThat(c.day()).isEqualTo(today.minusDays(1));
        });
    }

    @Test
    void a_withdrawal_ending_today_still_counts_and_one_ending_yesterday_does_not() {
        jdbc.update("INSERT INTO cow_health_events (id, tenant_id, cow_id, farm_id, event_date, event_type, "
                + "withdrawal_ends_on) VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), tenantA, cow1, farmA,
                today.minusDays(4), "TREATMENT", today);
        jdbc.update("INSERT INTO cow_health_events (id, tenant_id, cow_id, farm_id, event_date, event_type, "
                + "withdrawal_ends_on) VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), tenantA, cow2, farmA,
                today.minusDays(4), "TREATMENT", today.minusDays(1));

        assertThat(service.summary(farmA, null, null).underWithdrawal())
                .extracting(DairySummary.Withdrawal::tagNo).containsExactly("A001");
    }

    @Test
    void expected_calving_disappears_once_the_cow_has_calved() {
        breeding(cow1, today.minusDays(200), "PREGNANCY_CHECK", today.plusDays(40));
        breeding(cow2, today.minusDays(200), "PREGNANCY_CHECK", today.plusDays(30));
        breeding(cow2, today.minusDays(1), "CALVING", null);

        assertThat(service.summary(farmA, null, null).expectedCalvings())
                .extracting(DairySummary.ExpectedCalving::tagNo).containsExactly("A001");
    }

    @Test
    void another_tenants_farm_is_not_found_and_bad_ranges_are_refused() {
        assertThatThrownBy(() -> service.summary(farmB, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.summary(farmA, today, today.minusDays(1)))
                .isInstanceOf(DomainRuleException.class);
        assertThatThrownBy(() -> service.summary(farmA, today.minusDays(400), today))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    void the_database_itself_refuses_duplicates_and_impossible_numbers() {
        assertThatThrownBy(() -> cow(tenantA, farmA, "A001", "MILKING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        milk(tenantA, farmA, cow1, today, "MORNING", "5");
        assertThatThrownBy(() -> milk(tenantA, farmA, cow1, today, "MORNING", "6"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> milk(tenantA, farmA, cow1, today, "EVENING", "-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> delivery(tenantA, farmA, today, "10", "11", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO cow_health_events (id, tenant_id, cow_id, farm_id, "
                + "event_date, event_type, withdrawal_ends_on) VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), tenantA,
                cow1, farmA, today, "TREATMENT", today.minusDays(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // The same tag on another tenant's farm is fine: uniqueness is per tenant and farm.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cows WHERE tag_no = 'A001'", Integer.class)).isEqualTo(2);
    }

    private UUID farm(UUID tenant) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO farms (id, tenant_id, name, status) VALUES (?,?,?,?)", id, tenant, "F", "ACTIVE");
        return id;
    }

    private UUID cow(UUID tenant, UUID farm, String tag, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cows (id, tenant_id, farm_id, tag_no, sex, status) VALUES (?,?,?,?,?,?)",
                id, tenant, farm, tag, "FEMALE", status);
        return id;
    }

    private void milk(UUID tenant, UUID farm, UUID cow, LocalDate day, String session, String litres) {
        jdbc.update("INSERT INTO milk_yields (id, tenant_id, cow_id, farm_id, recorded_on, session, litres) "
                + "VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), tenant, cow, farm, day, session, new BigDecimal(litres));
    }

    private void delivery(UUID tenant, UUID farm, LocalDate day, String delivered, String rejected, String fat,
                          String price) {
        jdbc.update("INSERT INTO milk_deliveries (id, tenant_id, farm_id, delivered_on, buyer_name, litres_delivered, "
                + "litres_rejected, fat_pct, price_per_litre, status) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenant, farm, day, "Buyer", new BigDecimal(delivered), new BigDecimal(rejected),
                fat == null ? null : new BigDecimal(fat), price == null ? null : new BigDecimal(price), "DELIVERED");
    }

    private void breeding(UUID cow, LocalDate day, String type, LocalDate expected) {
        jdbc.update("INSERT INTO breeding_events (id, tenant_id, cow_id, farm_id, event_date, event_type, "
                + "expected_calving_on) VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), tenantA, cow, farmA, day, type,
                expected);
    }
}
