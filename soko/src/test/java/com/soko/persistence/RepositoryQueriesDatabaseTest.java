package com.soko.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.soko.api.Paging;
import com.soko.domain.MpesaPayment;
import com.soko.domain.PasswordReset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Runs the new search, paging, storefront and token queries against a real Postgres with the real
 * migrations, and lets Hibernate {@code validate} every entity against the migrated schema. Needs
 * a Postgres superuser (see TenantIsolationDatabaseTest for the container command); skipped, not
 * failed, when none answers. The connection is a superuser, so row-level security is not what is
 * under test here: the queries' own results are.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryQueriesDatabaseTest {

    private static final String HOST_URL =
            System.getenv().getOrDefault("SOKO_TEST_ADMIN_URL", "jdbc:postgresql://localhost:55437/");
    private static final String ADMIN = System.getenv().getOrDefault("SOKO_TEST_ADMIN_USER", "postgres");
    private static final String ADMIN_PW = System.getenv().getOrDefault("SOKO_TEST_ADMIN_PASSWORD", "ownerpw");
    private static final String DB = "soko_repo_it";

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
        registry.add("spring.flyway.url", () -> HOST_URL + DB);
        registry.add("spring.flyway.user", () -> ADMIN);
        registry.add("spring.flyway.password", () -> ADMIN_PW);
        registry.add("spring.flyway.placeholders.soko_app_password", () -> "app-password-for-tests-1");
        registry.add("spring.flyway.placeholders.soko_system_key", () -> "system-key-for-tests-0001");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ProductRepository products;
    @Autowired SupplierRepository suppliers;
    @Autowired CustomerRepository customers;
    @Autowired OfferRepository offers;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;
    @Autowired MpesaPaymentRepository payments;
    @Autowired PasswordResetRepository resets;

    private UUID tenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenants (id, name, slug) values (?, 'T', ?)", id, "t-" + id);
        return id;
    }

    private UUID product(UUID tenant, String sku, String name, String category) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into products (id, tenant_id, sku, name, category, unit, shelf_life_hours, list_price_cents)"
                + " values (?, ?, ?, ?, ?, 'kg', 48, 10000)", id, tenant, sku, name, category);
        return id;
    }

    private UUID supplier(UUID tenant, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into suppliers (id, tenant_id, name, county, lead_time_hours) values (?, ?, ?, 'Nakuru', 10)",
                id, tenant, name);
        return id;
    }

    private void offer(UUID tenant, UUID supplier, UUID product, long cost, int qty, String status) {
        jdbc.update("insert into offers (tenant_id, supplier_id, product_id, cost_cents, available_qty, status)"
                + " values (?, ?, ?, ?, ?, ?)", tenant, supplier, product, cost, qty, status);
    }

    @Test
    void theFiftyFirstProductIsReachableAndEveryPageIsStable() {
        UUID t = tenant();
        for (int i = 0; i < 120; i++) {
            product(t, "SKU-" + i, "Item %03d".formatted(i), "veg");
        }
        UUID other = tenant();
        product(other, "SKU-X", "Item 000", "veg");

        var first = products.search(t, Paging.like(""), PageRequest.of(0, 50));
        var third = products.search(t, Paging.like(""), PageRequest.of(2, 50));

        assertThat(first.getTotalElements()).isEqualTo(120);
        assertThat(first.getContent()).hasSize(50);
        assertThat(third.getContent()).hasSize(20);
        assertThat(third.getContent().get(19).getName()).isEqualTo("Item 119");
        // The 51st by name is on page two, not silently dropped.
        assertThat(products.search(t, Paging.like(""), PageRequest.of(1, 50)).getContent().get(0).getName())
                .isEqualTo("Item 050");
    }

    @Test
    void searchMatchesNameSkuAndCategoryAndTreatsWildcardsLiterally() {
        UUID t = tenant();
        product(t, "MLK-1", "Fresh Milk", "dairy");
        product(t, "EGG-1", "Eggs 100%", "poultry");
        product(t, "EGG-2", "Eggs tray", "poultry");

        assertThat(products.search(t, Paging.like("milk"), PageRequest.of(0, 10)).getContent())
                .extracting("sku").containsExactly("MLK-1");
        assertThat(products.search(t, Paging.like("egg-2"), PageRequest.of(0, 10)).getContent())
                .extracting("sku").containsExactly("EGG-2");
        assertThat(products.search(t, Paging.like("poultry"), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        // "%" typed by the user is a percent sign, not "match everything".
        assertThat(products.search(t, Paging.like("%"), PageRequest.of(0, 10)).getContent())
                .extracting("sku").containsExactly("EGG-1");
        assertThat(products.search(t, Paging.like("_"), PageRequest.of(0, 10)).getTotalElements()).isZero();
    }

    @Test
    void storefrontHidesOutOfStockInSqlAndPagesWithAnHonestTotal() {
        UUID t = tenant();
        UUID s = supplier(t, "Farm");
        for (int i = 0; i < 7; i++) {
            UUID p = product(t, "S" + i, "Crop " + i, "veg");
            // 0, 2 and 4 are in stock; 1, 3, 5 have none; 6 has only an inactive offer.
            if (i % 2 == 0 && i < 6) {
                offer(t, s, p, 100, 5, "ACTIVE");
            } else if (i == 6) {
                offer(t, s, p, 100, 5, "INACTIVE");
            } else {
                offer(t, s, p, 100, 0, "ACTIVE");
            }
        }
        List<Object[]> page = offers.storefront(t, "%", 2, 0);
        assertThat(page).extracting(r -> r[2]).containsExactly("Crop 0", "Crop 2");
        assertThat(offers.storefront(t, "%", 2, 2)).extracting(r -> r[2]).containsExactly("Crop 4");
        assertThat(offers.countStorefront(t, "%")).isEqualTo(3);
        assertThat(offers.storefront(t, Paging.like("crop 2"), 10, 0)).hasSize(1);
        assertThat(offers.countStorefront(t, Paging.like("nothing"))).isZero();
    }

    @Test
    void offerSearchFindsByProductOrSupplierNameCheapestFirst() {
        UUID t = tenant();
        UUID milk = product(t, "M", "Milk", "dairy");
        UUID eggs = product(t, "E", "Eggs", "poultry");
        UUID a = supplier(t, "Alpha Dairy");
        UUID b = supplier(t, "Beta Farm");
        offer(t, a, milk, 300, 5, "ACTIVE");
        offer(t, b, milk, 200, 5, "ACTIVE");
        offer(t, b, eggs, 900, 5, "ACTIVE");

        var byProduct = offers.search(t, Paging.like("milk"), PageRequest.of(0, 10));
        assertThat(byProduct.getTotalElements()).isEqualTo(2);
        assertThat(byProduct.getContent()).extracting("costCents").containsExactly(200L, 300L);
        assertThat(offers.search(t, Paging.like("beta"), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void orderListPagesAndSearchesByReferenceOrCustomer() {
        UUID t = tenant();
        UUID c = UUID.randomUUID();
        jdbc.update("insert into customers (id, tenant_id, name, phone, county) values (?, ?, 'Naivas', '0700', 'Kiambu')", c, t);
        for (int i = 0; i < 5; i++) {
            jdbc.update("insert into orders (tenant_id, reference, customer_id, placed_at) values (?, ?, ?, now() - (? * interval '1 minute'))",
                    t, "SO-" + i, c, i);
        }
        assertThat(orders.countWithCustomer(t, "%")).isEqualTo(5);
        List<Object[]> first = orders.listWithCustomer(t, "%", 2, 0);
        List<Object[]> last = orders.listWithCustomer(t, "%", 2, 4);
        assertThat(first).extracting(r -> r[1]).containsExactly("SO-0", "SO-1");
        assertThat(last).extracting(r -> r[1]).containsExactly("SO-4");
        assertThat(orders.countWithCustomer(t, Paging.like("naiv"))).isEqualTo(5);
        assertThat(orders.countWithCustomer(t, Paging.like("so-3"))).isEqualTo(1);
    }

    @Test
    void supplierCustomerAndUserSearchPage() {
        UUID t = tenant();
        supplier(t, "Rift Valley Dairy");
        supplier(t, "Coast Fisheries");
        jdbc.update("insert into customers (tenant_id, name, phone, county) values (?, 'Quickmart', '0711000111', 'Nairobi')", t);
        jdbc.update("insert into users (tenant_id, email, full_name, password_hash) values (?, 'amina@x.test', 'Amina W', 'x')", t);
        jdbc.update("insert into users (tenant_id, email, full_name, password_hash) values (?, 'brian@x.test', 'Brian O', 'x')", t);

        assertThat(suppliers.search(t, Paging.like("dairy"), PageRequest.of(0, 5)).getContent())
                .extracting("name").containsExactly("Rift Valley Dairy");
        assertThat(customers.search(t, Paging.like("0711"), PageRequest.of(0, 5)).getTotalElements()).isEqualTo(1);
        assertThat(users.search(t, Paging.like("amina"), PageRequest.of(0, 5)).getContent())
                .extracting("email").containsExactly("amina@x.test");
        assertThat(users.search(t, Paging.like(""), PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void orderIdempotencyKeyIsUniquePerTenantButFreeAcrossTenants() {
        UUID t1 = tenant();
        UUID t2 = tenant();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        jdbc.update("insert into customers (id, tenant_id, name, phone, county) values (?, ?, 'A', '1', 'x')", c1, t1);
        jdbc.update("insert into customers (id, tenant_id, name, phone, county) values (?, ?, 'B', '1', 'x')", c2, t2);
        jdbc.update("insert into orders (tenant_id, reference, customer_id, idempotency_key) values (?, 'R1', ?, 'key-1')", t1, c1);
        jdbc.update("insert into orders (tenant_id, reference, customer_id, idempotency_key) values (?, 'R2', ?, 'key-1')", t2, c2);
        // Orders with no key never collide with each other.
        jdbc.update("insert into orders (tenant_id, reference, customer_id) values (?, 'R3', ?)", t1, c1);
        jdbc.update("insert into orders (tenant_id, reference, customer_id) values (?, 'R4', ?)", t1, c1);

        assertThat(orders.findByTenantIdAndIdempotencyKey(t1, "key-1")).isPresent();
        assertThat(orders.findByTenantIdAndIdempotencyKey(t2, "key-1").get().getReference()).isEqualTo("R2");
        // Last: a unique violation aborts the surrounding transaction.
        assertThatThrownBy(() -> jdbc.update(
                "insert into orders (tenant_id, reference, customer_id, idempotency_key) values (?, 'R5', ?, 'key-1')", t1, c1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orphanedPaymentsAreListedNewestFirstForTheirOwnTenantOnly() {
        UUID t = tenant();
        UUID other = tenant();
        insertPayment(t, "ORPHANED", "ws_1", "2026-01-01T10:00:00Z", "order missing");
        insertPayment(t, "ORPHANED", "ws_2", "2026-01-02T10:00:00Z", "order cancelled");
        insertPayment(t, "SUCCESS", "ws_3", "2026-01-03T10:00:00Z", null);
        insertPayment(other, "ORPHANED", "ws_4", "2026-01-04T10:00:00Z", "x");

        List<MpesaPayment> found = payments.findByTenantIdAndStatusOrderByInitiatedAtDesc(
                t, "ORPHANED", PageRequest.of(0, 10));
        assertThat(found).extracting(MpesaPayment::getCheckoutRequestId).containsExactly("ws_2", "ws_1");
        assertThat(found.get(0).getStatus()).isEqualTo(MpesaPayment.Status.ORPHANED);
        assertThat(found.get(0).getOrphanReason()).isEqualTo("order cancelled");
    }

    private void insertPayment(UUID tenant, String status, String checkout, String at, String reason) {
        jdbc.update("insert into mpesa_payments (tenant_id, purpose, reference_id, msisdn, amount_cents, due_cents,"
                + " checkout_request_id, status, orphan_reason, initiated_at) values (?, 'ORDER', ?, '254700000000', 100, 100, ?, ?, ?, ?::timestamptz)",
                tenant, UUID.randomUUID(), checkout, status, reason, at);
    }

    @Test
    void aResetTokenCanBeConsumedOnceAndNeverAfterItExpires() {
        UUID t = tenant();
        UUID user = UUID.randomUUID();
        jdbc.update("insert into users (id, tenant_id, email, full_name, password_hash) values (?, ?, 'r@x.test', 'R', 'x')", user, t);

        PasswordReset live = reset(t, user, "hash-live", Instant.now().plusSeconds(600));
        PasswordReset expired = reset(t, user, "hash-expired", Instant.now().minusSeconds(5));

        assertThat(resets.consume(live.getId(), Instant.now())).isEqualTo(1);
        assertThat(resets.consume(live.getId(), Instant.now())).isZero();
        assertThat(resets.consume(expired.getId(), Instant.now())).isZero();
        assertThat(resets.findByTokenHash("hash-live")).isPresent();
    }

    @Test
    void aNewRequestVoidsEarlierUnusedTokens() {
        UUID t = tenant();
        UUID user = UUID.randomUUID();
        jdbc.update("insert into users (id, tenant_id, email, full_name, password_hash) values (?, ?, 'v@x.test', 'V', 'x')", user, t);
        PasswordReset older = reset(t, user, "hash-older", Instant.now().plusSeconds(600));

        assertThat(resets.voidOutstanding(user, Instant.now())).isEqualTo(1);
        assertThat(resets.consume(older.getId(), Instant.now())).isZero();
    }

    private PasswordReset reset(UUID tenant, UUID user, String hash, Instant expires) {
        PasswordReset reset = new PasswordReset();
        reset.setTenantId(tenant);
        reset.setUserId(user);
        reset.setTokenHash(hash);
        reset.setExpiresAt(expires);
        PasswordReset saved = resets.saveAndFlush(reset);
        return saved;
    }
}
