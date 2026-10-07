package com.kenyarealestate.property.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.kenyarealestate.property.entity.Property;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The native public-search query against a real Postgres with the real migrations: filters, the
 * total, ordering, and paging through rows that tie on the sort key. A mock repository cannot say
 * any of this, and the query is the part of the service a visitor actually uses.
 *
 * <p>Needs a Postgres superuser: {@code docker run -d --name smartre-it-pg -e POSTGRES_PASSWORD=ownerpw
 * -p 55437:5432 postgres:16}. Override with SMARTRE_TEST_ADMIN_URL / _USER / _PASSWORD. Skipped, not
 * failed, when none answers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PropertySearchQueryDatabaseTest {

    private static final String HOST_URL =
            System.getenv().getOrDefault("SMARTRE_TEST_ADMIN_URL", "jdbc:postgresql://localhost:55437/");
    private static final String ADMIN = System.getenv().getOrDefault("SMARTRE_TEST_ADMIN_USER", "postgres");
    private static final String ADMIN_PW = System.getenv().getOrDefault("SMARTRE_TEST_ADMIN_PASSWORD", "ownerpw");
    private static final String DB = "smartre_property_it";

    @BeforeAll
    static void createDatabase() {
        try (Connection admin = DriverManager.getConnection(HOST_URL + "postgres", ADMIN, ADMIN_PW);
                Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + DB);
        } catch (SQLException unavailable) {
            assumeTrue(false, "no Postgres at " + HOST_URL + " (" + unavailable.getMessage() + ")");
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> HOST_URL + DB);
        registry.add("spring.datasource.username", () -> ADMIN);
        registry.add("spring.datasource.password", () -> ADMIN_PW);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropertyRepository repo;

    private int minutes;

    /** Inserts one listing; created_at steps back one minute per call so the default order is known. */
    private UUID listing(String status, String county, String city, String type, String listingType, long price,
            Integer bedrooms, String title, String description, boolean verified, int views, Integer ageMinutes) {
        UUID id = UUID.randomUUID();
        int age = ageMinutes != null ? ageMinutes : ++minutes;
        jdbc.update("""
                insert into properties (id, seller_id, title, description, property_type, listing_type, status, county,
                    city, price, bedrooms, seller_identity_verified, property_ownership_verified, view_count, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now() - (? * interval '1 minute'))
                """, id, UUID.randomUUID(), title, description, type, listingType, status, county, city,
                BigDecimal.valueOf(price), bedrooms, verified, verified, views, age);
        return id;
    }

    private UUID active(String title, long price, Integer bedrooms) {
        return listing("ACTIVE", "Nairobi", "Westlands", "APARTMENT", "SALE", price, bedrooms, title, null, false, 0, null);
    }

    private List<Property> search(String county, String city, String type, String lt, BigDecimal min, BigDecimal max,
            Integer minBed, String kw, boolean verifiedOnly, String sortKey, boolean asc, int page, int size) {
        return repo.search(county, city, type, lt, min, max, minBed, kw, verifiedOnly, sortKey, asc,
                PageRequest.of(page, size)).getContent();
    }

    private static final String ANY_KEY = "createdAt";

    @Test
    void onlyActiveListingsAreEverReturnedAndTheTotalAgrees() {
        UUID shown = active("Shown", 5_000_000, 2);
        for (String hidden : List.of("DRAFT", "PENDING_VERIFICATION", "SUSPENDED", "SOLD", "RENTED", "WITHDRAWN", "UNLISTED")) {
            listing(hidden, "Nairobi", "Westlands", "APARTMENT", "SALE", 1_000_000, 2, "Hidden " + hidden, null, false, 0, null);
        }

        var page = repo.search("Nairobi", null, null, null, null, null, null, null, false, ANY_KEY, false, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Property::getId).containsExactly(shown);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void everyFilterNarrowsAndTheTotalFollowsTheFilter() {
        listing("ACTIVE", "Nairobi", "Westlands", "APARTMENT", "SALE", 8_000_000, 3, "Sunny flat", "near the park", true, 0, null);
        listing("ACTIVE", "Nairobi", "Kilimani", "APARTMENT", "RENT", 80_000, 1, "Studio", "quiet street", false, 0, null);
        listing("ACTIVE", "Mombasa", "Nyali", "HOUSE", "SALE", 20_000_000, 5, "Beach villa", "sea view", true, 0, null);
        listing("ACTIVE", "Kiambu", null, "LAND", "SALE", 3_000_000, null, "Plot", "title ready", false, 0, null);

        assertThat(count("nairobi", null, null, null, null, null, null, null, false)).as("county, any case").isEqualTo(2);
        assertThat(count(null, "kilim", null, null, null, null, null, null, false)).as("city, partial").isEqualTo(1);
        assertThat(count(null, null, "HOUSE", null, null, null, null, null, false)).as("type").isEqualTo(1);
        assertThat(count(null, null, null, "RENT", null, null, null, null, false)).as("listing type").isEqualTo(1);
        assertThat(count(null, null, null, null, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(10_000_000), null, null, false))
                .as("price range, inclusive").isEqualTo(1);
        assertThat(count(null, null, null, null, null, null, 3, null, false)).as("minimum bedrooms excludes land").isEqualTo(2);
        assertThat(count(null, null, null, null, null, null, null, "SEA", false)).as("keyword in description").isEqualTo(1);
        assertThat(count(null, null, null, null, null, null, null, "studio", false)).as("keyword in title").isEqualTo(1);
        assertThat(count(null, null, null, null, null, null, null, null, true)).as("verified only needs both flags").isEqualTo(2);
        assertThat(count("Nairobi", null, "APARTMENT", "SALE", null, null, 2, "park", true)).as("combined").isEqualTo(1);
        assertThat(count("Nowhere", null, null, null, null, null, null, null, false)).isZero();
    }

    private long count(String county, String city, String type, String lt, BigDecimal min, BigDecimal max,
            Integer minBed, String kw, boolean verified) {
        return repo.search(county, city, type, lt, min, max, minBed, kw, verified, ANY_KEY, false, PageRequest.of(0, 50))
                .getTotalElements();
    }

    @Test
    void theDefaultOrderIsNewestFirstAndOldestFirstWhenAsked() {
        UUID oldest = listing("ACTIVE", "Nairobi", "Westlands", "APARTMENT", "SALE", 1, 1, "A", null, false, 0, 30);
        UUID middle = listing("ACTIVE", "Nairobi", "Westlands", "APARTMENT", "SALE", 1, 1, "B", null, false, 0, 20);
        UUID newest = listing("ACTIVE", "Nairobi", "Westlands", "APARTMENT", "SALE", 1, 1, "C", null, false, 0, 0);

        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "createdAt", false, 0, 10)))
                .containsExactly(newest, middle, oldest);
        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "createdAt", true, 0, 10)))
                .containsExactly(oldest, middle, newest);
    }

    @Test
    void sortingByPriceInEitherDirectionIsHonoured() {
        UUID cheap = active("cheap", 1_000_000, 1);
        UUID mid = active("mid", 5_000_000, 1);
        UUID dear = active("dear", 9_000_000, 1);

        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "price", true, 0, 10)))
                .containsExactly(cheap, mid, dear);
        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "price", false, 0, 10)))
                .containsExactly(dear, mid, cheap);
    }

    @Test
    void aListingWithNoBedroomCountSortsLastInBothDirections() {
        UUID land = active("land", 1, null);
        UUID studio = active("studio", 1, 1);
        UUID villa = active("villa", 1, 5);

        // Postgres puts NULLs first in a descending sort, so without NULLS LAST a plot of land
        // would top "most bedrooms".
        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "bedrooms", false, 0, 10)))
                .containsExactly(villa, studio, land);
        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "bedrooms", true, 0, 10)))
                .containsExactly(studio, villa, land);
    }

    @Test
    void sortingByViewCountPutsTheMostViewedFirst() {
        UUID quiet = listing("ACTIVE", "Nairobi", "W", "APARTMENT", "SALE", 1, 1, "q", null, false, 2, null);
        UUID popular = listing("ACTIVE", "Nairobi", "W", "APARTMENT", "SALE", 1, 1, "p", null, false, 900, null);

        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "viewCount", false, 0, 10)))
                .containsExactly(popular, quiet);
    }

    @Test
    void pagingThroughRowsThatAllTieOnPriceNeverRepeatsOrSkipsAListing() {
        // Twelve listings at one price, with several sharing a creation time too: the tiebreak is the id.
        Set<UUID> all = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            all.add(listing("ACTIVE", "Nairobi", "W", "APARTMENT", "SALE", 4_000_000, 2, "tie " + i, null, false, 0, i / 4));
        }

        List<UUID> walked = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            walked.addAll(ids(search(null, null, null, null, null, null, null, null, false, "price", true, page, 5)));
        }

        assertThat(walked).as("each listing exactly once across pages").hasSize(12).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(all);
        // And the order is reproducible: the same request twice gives the same page.
        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "price", true, 1, 5)))
                .isEqualTo(ids(search(null, null, null, null, null, null, null, null, false, "price", true, 1, 5)));
    }

    @Test
    void anUnknownSortKeyFallsBackToNewestFirstRatherThanFailing() {
        UUID older = listing("ACTIVE", "Nairobi", "W", "APARTMENT", "SALE", 1, 1, "older", null, false, 0, 10);
        UUID newer = listing("ACTIVE", "Nairobi", "W", "APARTMENT", "SALE", 1, 1, "newer", null, false, 0, 0);

        assertThat(ids(search(null, null, null, null, null, null, null, null, false, "nonsense", true, 0, 10)))
                .containsExactly(newer, older);
    }

    private static List<UUID> ids(List<Property> found) {
        return found.stream().map(Property::getId).toList();
    }
}
