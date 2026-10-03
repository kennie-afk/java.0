package com.mara.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.core.sales.FeedEntry;
import com.mara.core.sales.SaleIngestService;
import com.mara.core.sales.SyncFeedPoller;
import com.mara.kit.auth.TerminalDirectory;
import com.mara.kit.auth.TerminalRecord;
import com.mara.kit.tenant.TenantContext;
import com.mara.platform.identity.RequestSignature;
import com.mara.platform.sale.SaleBody;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * core-service against a real PostgreSQL, through HTTP and through the ingest service:
 * fiscal leases, the double-entry ledger and its database-enforced invariants, tenant
 * isolation, and idempotency under concurrency. Needs {@code -Dmara.test.jdbc.url}.
 * Each test uses fresh tenants and terminals because the ledger is append-only by design.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoreIntegrationTest {

    private static final String JDBC_URL = System.getProperty("mara.test.jdbc.url");
    private static final String INTERNAL = com.mara.platform.credential.OperatorToken.mint().token();
    private static final String ADMIN = com.mara.platform.credential.OperatorToken.mint().token();
    private static final HttpServer SYNC = startFakeSync();
    private static final Map<String, List<FeedEntry>> FEED = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        org.junit.jupiter.api.Assumptions.assumeTrue(JDBC_URL != null && !JDBC_URL.isBlank(), "needs -Dmara.test.jdbc.url");
        r.add("mara.datasource.owner.url", () -> JDBC_URL);
        r.add("mara.datasource.owner.username", () -> System.getProperty("mara.test.owner.user", "mara_owner"));
        r.add("mara.datasource.owner.password", () -> System.getProperty("mara.test.owner.password", "owner-secret"));
        r.add("mara.datasource.app.url", () -> JDBC_URL);
        r.add("mara.datasource.app.username", () -> "mara_app");
        r.add("mara.datasource.app.password", () -> "app-secret");
        r.add("mara.identity.base-url", () -> "http://localhost:1");
        r.add("mara.sync.base-url", () -> "http://127.0.0.1:" + SYNC.getAddress().getPort());
        r.add("mara.sync.poll-interval-ms", () -> "3600000");
        r.add("mara.sync.initial-delay-ms", () -> "3600000");
        r.add("mara.service.credential", () -> INTERNAL);
        r.add("mara.fiscal.lease-size", () -> "100");
    }

    @TestConfiguration
    static class Directory {
        static final Map<String, TerminalRecord> TERMINALS = new ConcurrentHashMap<>();

        @Bean
        @Primary
        TerminalDirectory testDirectory() {
            return id -> Optional.ofNullable(TERMINALS.get(id));
        }

        /** Stands in for identity-service's verdicts: the test's two credentials and what each may do. */
        @Bean
        @Primary
        com.mara.kit.auth.CredentialVerifier testVerifier() {
            return req -> {
                java.util.Set<String> scopes = req.presented().equals(ADMIN)
                        ? java.util.Set.of("admin:read", "admin:write")
                        : req.presented().equals(INTERNAL)
                        ? java.util.Set.of("sync:feed", "terminals:lookup", "credentials:verify")
                        : null;
                if (scopes == null) {
                    return com.mara.kit.auth.CredentialVerifier.Decision.denied(401, "unknown_credential");
                }
                return scopes.contains(req.requiredScope())
                        ? com.mara.kit.auth.CredentialVerifier.Decision.allowed("test", "test", null)
                        : com.mara.kit.auth.CredentialVerifier.Decision.denied(403, "scope_denied");
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SaleIngestService ingest;
    @Autowired SyncFeedPoller poller;
    @Autowired @Qualifier("ownerDataSource") DataSource owner;
    @Autowired DataSource app;

    // ------------------------------------------------------------- fixtures

    static final class Till {
        final String id = "TERM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        final String tenant;
        final KeyPair key;
        long sequence = 0;

        Till(String tenant) {
            this.tenant = tenant;
            try {
                key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            Directory.TERMINALS.put(id, new TerminalRecord(id, tenant, "BR-1",
                    Base64.getEncoder().encodeToString(key.getPublic().getEncoded()), "ACTIVE"));
        }

        Map<String, String> headers(String method, String path, byte[] body, long at) {
            try {
                Signature s = Signature.getInstance("Ed25519");
                s.initSign(key.getPrivate());
                s.update(RequestSignature.message(id, at, method, path, body));
                return Map.of("X-Mara-Terminal", id, "X-Mara-Timestamp", String.valueOf(at),
                        "X-Mara-Signature", HexFormat.of().formatHex(s.sign()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static String newTenant() {
        return "TEN-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions signedPost(Till t, String path, Object body) throws Exception {
        byte[] bytes = json.writeValueAsBytes(body == null ? Map.of() : body);
        MockHttpServletRequestBuilder b = post(path).contentType(MediaType.APPLICATION_JSON).content(bytes);
        t.headers("POST", path, bytes, Instant.now().getEpochSecond()).forEach(b::header);
        return mvc.perform(b);
    }

    private JsonNode lease(Till t) throws Exception {
        return json.readTree(signedPost(t, "/v1/terminal/fiscal/leases?request=" + UUID.randomUUID(), null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void askingForALeaseTwiceWithTheSameRequestIdReturnsTheSameLeaseAndTakesNoMoreNumbers() throws Exception {
        Till t = new Till(newTenant());
        String path = "/v1/terminal/fiscal/leases?request=" + UUID.randomUUID();
        JsonNode first = json.readTree(signedPost(t, path, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        // A retry after a lost response: same request id, answered 200 with the same block.
        JsonNode again = json.readTree(signedPost(t, path, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(again.get("replayed").asBoolean()).isTrue();
        assertThat(again.get("leaseId").asText()).isEqualTo(first.get("leaseId").asText());
        assertThat(again.get("firstNumber").asText()).isEqualTo(first.get("firstNumber").asText());
        // The replay did not consume the lease limit: a different request still gets the next block.
        JsonNode next = lease(t);
        assertThat(Long.parseLong(next.get("firstNumber").asText()))
                .isEqualTo(Long.parseLong(first.get("lastNumber").asText()) + 1);
    }

    @Test
    void aCapturedLeaseRequestReplayedWithinTheSignatureWindowCannotTakeAnotherBlock() throws Exception {
        Till t = new Till(newTenant());
        // No request id: the signature stands in for it. The same signed bytes, sent twice.
        byte[] body = json.writeValueAsBytes(Map.of());
        long at = Instant.now().getEpochSecond();
        Map<String, String> headers = t.headers("POST", "/v1/terminal/fiscal/leases", body, at);
        MockHttpServletRequestBuilder one = post("/v1/terminal/fiscal/leases").contentType(MediaType.APPLICATION_JSON).content(body);
        headers.forEach(one::header);
        String firstBody = mvc.perform(one).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequestBuilder replay = post("/v1/terminal/fiscal/leases").contentType(MediaType.APPLICATION_JSON).content(body);
            headers.forEach(replay::header);
            JsonNode r = json.readTree(mvc.perform(replay).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(r.get("leaseId").asText()).isEqualTo(json.readTree(firstBody).get("leaseId").asText());
        }
        assertThat(admin("/v1/admin/fiscal/leases", t.tenant)).hasSize(1);
    }

    @Test
    void aMalformedLeaseRequestIdIsRefused() throws Exception {
        Till t = new Till(newTenant());
        signedPost(t, "/v1/terminal/fiscal/leases?request=short", null).andExpect(status().isBadRequest());
    }

    @Test
    void theSalesReportGroupsByLocalDayTerminalAndPaymentMethodAndStaysInsideItsTenant() throws Exception {
        String tenant = newTenant();
        Till a = new Till(tenant);
        Till b = new Till(tenant);
        long noon = java.time.Instant.parse("2025-10-09T09:00:00Z").getEpochSecond();          // 12:00 in Nairobi
        long lateEvening = java.time.Instant.parse("2025-10-09T20:30:00Z").getEpochSecond();   // 23:30 in Nairobi, 20:30 UTC
        long nextMorning = java.time.Instant.parse("2025-10-10T04:00:00Z").getEpochSecond();   // 07:00 in Nairobi
        // till A: a cash sale (11,600) and a split sale (17,400 = 10,000 cash + 7,400 mobile money)
        a.sequence++;
        postSale(tenant, new FeedEntry(a.id, tenant, a.sequence, noon, 0, json.writeValueAsString(cashSale(10000, 1600, null))));
        a.sequence++;
        postSale(tenant, new FeedEntry(a.id, tenant, a.sequence, lateEvening, 0, json.writeValueAsString(sale(List.of(
                new SaleBody.Payment("CASH", "10000", "", "10000"), new SaleBody.Payment("MOBILE_MONEY", "7400", "R1", "7400")),
                "17400", null, 15000, 2400))));
        // till B: a mobile sale (5,800) after midnight in Nairobi
        b.sequence++;
        postSale(tenant, new FeedEntry(b.id, tenant, b.sequence, nextMorning, 0, json.writeValueAsString(sale(List.of(
                new SaleBody.Payment("MOBILE_MONEY", "5800", "R2", "5800")), "5800", null, 5000, 800))));

        var byDay = admin("/v1/admin/reports/sales?from=2025-10-09&to=2025-10-10&by=day", tenant);
        assertThat(byDay).hasSize(2);
        var oct9 = byDay.stream().filter(r -> r.get("day").toString().equals("2025-10-09")).findFirst().orElseThrow();
        // 23:30 in Nairobi (20:30 UTC) is still the 9th; 07:00 on the 10th is the 10th
        assertThat(((Number) oct9.get("sales")).longValue()).isEqualTo(2);
        assertThat(((Number) oct9.get("totalMinor")).longValue()).isEqualTo(11600 + 17400);
        assertThat(((Number) oct9.get("cashMinor")).longValue()).isEqualTo(11600 + 10000);
        assertThat(((Number) oct9.get("mobileMinor")).longValue()).isEqualTo(7400);
        assertThat(((Number) oct9.get("fiscalPending")).longValue()).isEqualTo(2);
        var oct10 = byDay.stream().filter(r -> r.get("day").toString().equals("2025-10-10")).findFirst().orElseThrow();
        assertThat(((Number) oct10.get("mobileMinor")).longValue()).isEqualTo(5800);

        // in UTC the 23:30 Nairobi sale is 20:30 and the 07:00 one is 04:00: still the same two days here, but
        // asking for UTC must not change the totals across the whole range
        var utc = admin("/v1/admin/reports/sales?from=2025-10-09&to=2025-10-10&by=day&zone=UTC", tenant);
        assertThat(utc.stream().mapToLong(r -> ((Number) r.get("totalMinor")).longValue()).sum()).isEqualTo(11600 + 17400 + 5800);

        var byTerminal = admin("/v1/admin/reports/sales?from=2025-10-09&to=2025-10-10&by=terminal", tenant);
        assertThat(byTerminal.stream().map(r -> r.get("terminalId")).distinct()).containsExactlyInAnyOrder(a.id, b.id);
        var byCashier = admin("/v1/admin/reports/sales?from=2025-10-09&to=2025-10-10&by=cashier", tenant);
        assertThat(byCashier.get(0)).containsKey("cashierStaffId");

        // another tenant sees none of it
        assertThat(admin("/v1/admin/reports/sales?from=2025-10-09&to=2025-10-10&by=day", newTenant())).isEmpty();

        // bounded and validated
        for (String bad : new String[] {"from=2025-10-10&to=2025-10-09", "from=2025-01-01&to=2025-06-01", "from=2025-10-09&to=2025-10-10&by=sku",
                "from=2025-10-09&to=2025-10-10&zone=Mars/Base"}) {
            mvc.perform(get("/v1/admin/reports/sales?" + bad).header("Authorization", "Bearer " + ADMIN).header("X-Mara-Tenant", tenant))
                    .andExpect(status().isBadRequest());
        }
    }

    private static SaleBody sale(List<SaleBody.Payment> payments, String total, String fiscalNumber, long net, long tax) {
        return new SaleBody(SaleBody.V1, "KES",
                List.of(new SaleBody.Line("SKU", "Item", 1, String.valueOf(net), net == 0 ? 0 : (int) (tax * 10000 / net),
                        String.valueOf(net), String.valueOf(tax))),
                payments, total,
                fiscalNumber == null ? new SaleBody.Fiscal(SaleBody.Fiscal.PENDING, "")
                        : new SaleBody.Fiscal(SaleBody.Fiscal.NUMBERED, fiscalNumber),
                null);
    }

    private static SaleBody cashSale(long net, long tax, String fiscalNumber) {
        String total = String.valueOf(net + tax);
        return sale(List.of(new SaleBody.Payment("CASH", total, "", total)), total, fiscalNumber, net, tax);
    }

    private FeedEntry feed(Till t, SaleBody sale) throws Exception {
        t.sequence++;
        return new FeedEntry(t.id, t.tenant, t.sequence, 1_760_000_000L + t.sequence, 0, json.writeValueAsString(sale));
    }

    private SaleIngestService.Outcome postSale(String tenant, FeedEntry e) {
        return TenantContext.with(tenant, () -> ingest.ingest(e));
    }

    private List<Map<String, Object>> admin(String path, String tenant) throws Exception {
        String body = mvc.perform(get(path).header("Authorization", "Bearer " + ADMIN).header("X-Mara-Tenant", tenant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(body, new TypeReference<>() {
        });
    }

    private Map<String, Long> balances(String tenant) throws Exception {
        Map<String, Long> out = new java.util.HashMap<>();
        for (Map<String, Object> row : admin("/v1/admin/ledger/trial-balance", tenant)) {
            out.put((String) row.get("account"), ((Number) row.get("balanceMinor")).longValue());
        }
        return out;
    }

    private void assertBooksBalance(String tenant) throws Exception {
        long debit = 0;
        long credit = 0;
        for (Map<String, Object> row : admin("/v1/admin/ledger/trial-balance", tenant)) {
            debit += ((Number) row.get("debitMinor")).longValue();
            credit += ((Number) row.get("creditMinor")).longValue();
        }
        assertThat(debit).as("total debits equal total credits").isEqualTo(credit);
    }

    // ---------------------------------------------------------------- fiscal

    @Test
    void leasesAreDisjointAcrossTerminalsAndCappedPerTerminal() throws Exception {
        String tenant = newTenant();
        Till a = new Till(tenant);
        Till b = new Till(tenant);
        JsonNode a1 = lease(a);
        JsonNode b1 = lease(b);
        JsonNode a2 = lease(a);
        assertThat(a1.get("firstNumber").asText()).isEqualTo("1");
        assertThat(a1.get("lastNumber").asText()).isEqualTo("100");
        assertThat(b1.get("firstNumber").asText()).isEqualTo("101");
        assertThat(a2.get("firstNumber").asText()).isEqualTo("201");
        assertThat(a1.get("nextNumber").asText()).isEqualTo("1");

        // A third live lease for the same terminal is refused rather than hoarded.
        signedPost(a, "/v1/terminal/fiscal/leases?request=" + UUID.randomUUID(), null).andExpect(status().isConflict());

        // Another tenant has its own number space, starting again at 1.
        Till other = new Till(newTenant());
        assertThat(lease(other).get("firstNumber").asText()).isEqualTo("1");
    }

    @Test
    void concurrentRequestsNeverOverlapAndLeaveNoHole() throws Exception {
        String tenant = newTenant();
        int n = 20;
        List<Till> tills = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tills.add(new Till(tenant));
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<JsonNode>> futures = new ArrayList<>();
        for (Till t : tills) {
            futures.add(pool.submit(() -> lease(t)));
        }
        List<long[]> ranges = new ArrayList<>();
        for (Future<JsonNode> f : futures) {
            JsonNode l = f.get();
            ranges.add(new long[] {l.get("firstNumber").asLong(), l.get("lastNumber").asLong()});
        }
        pool.shutdown();
        ranges.sort((x, y) -> Long.compare(x[0], y[0]));
        long expectedFirst = 1;
        for (long[] r : ranges) {
            assertThat(r[0]).as("contiguous and disjoint").isEqualTo(expectedFirst);
            expectedFirst = r[1] + 1;
        }
        assertThat(expectedFirst).isEqualTo(n * 100L + 1);
    }

    @Test
    void unusedNumbersAreVoidedOnReturnAndNeverIssuedAgain() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        JsonNode l = lease(t);
        String path = "/v1/terminal/fiscal/leases/" + l.get("leaseId").asText() + "/return";

        signedPost(t, path, Map.of("nextUnused", "500")).andExpect(status().isBadRequest());
        JsonNode back = json.readTree(signedPost(t, path, Map.of("nextUnused", "41")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(back.get("voidedFrom").asLong()).isEqualTo(41);
        assertThat(back.get("voidedTo").asLong()).isEqualTo(100);
        // Idempotent for a repeat.
        assertThat(json.readTree(signedPost(t, path, Map.of("nextUnused", "41")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("alreadyReturned").asBoolean()).isTrue();
        // The next lease starts after the whole returned block: 41..100 are not recycled.
        assertThat(lease(t).get("firstNumber").asText()).isEqualTo("101");
        // Another terminal cannot return this terminal's lease.
        signedPost(new Till(tenant), path, Map.of("nextUnused", "10")).andExpect(status().isNotFound());

        var leases = admin("/v1/admin/fiscal/leases", tenant);
        var first = leases.stream().filter(m -> ((Number) m.get("id")).longValue() == l.get("leaseId").asLong()).findFirst().get();
        assertThat(((Number) first.get("voided")).longValue()).isEqualTo(60);
    }

    @Test
    void aTerminalCannotVoidNumbersItsIngestedSalesAlreadySpent() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        JsonNode l = lease(t);
        postSale(tenant, feed(t, cashSale(10000, 1600, "5")));
        String path = "/v1/terminal/fiscal/leases/" + l.get("leaseId").asText() + "/return";
        signedPost(t, path, Map.of("nextUnused", "3")).andExpect(status().isConflict());
        signedPost(t, path, Map.of("nextUnused", "6")).andExpect(status().isOk());
    }

    @Test
    void fiscalEndpointsNeedASignedRequestFromAnActiveTerminal() throws Exception {
        mvc.perform(post("/v1/terminal/fiscal/leases").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        Till t = new Till(newTenant());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequestBuilder stale = post("/v1/terminal/fiscal/leases").contentType(MediaType.APPLICATION_JSON).content(body);
        t.headers("POST", "/v1/terminal/fiscal/leases", body, Instant.now().getEpochSecond() - 7200).forEach(stale::header);
        mvc.perform(stale).andExpect(status().isUnauthorized());
        Directory.TERMINALS.put(t.id, new TerminalRecord(t.id, t.tenant, "BR-1",
                Base64.getEncoder().encodeToString(t.key.getPublic().getEncoded()), "SUSPENDED"));
        signedPost(t, "/v1/terminal/fiscal/leases", null).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- ledger

    @Test
    void aSalePostsBalancedAndReplayingItChangesNothing() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        FeedEntry e = feed(t, cashSale(14000, 2240, null));

        assertThat(postSale(tenant, e)).isEqualTo(SaleIngestService.Outcome.POSTED);
        assertThat(balances(tenant)).containsEntry("CASH", 16240L).containsEntry("SALES", -14000L)
                .containsEntry("VAT_PAYABLE", -2240L).doesNotContainKey("SUSPENSE");
        assertBooksBalance(tenant);

        assertThat(postSale(tenant, e)).isEqualTo(SaleIngestService.Outcome.ALREADY_POSTED);
        assertThat(balances(tenant)).containsEntry("CASH", 16240L);
        assertThat(admin("/v1/admin/sales", tenant)).hasSize(1);
        assertThat(admin("/v1/admin/exceptions", tenant)).isEmpty();
    }

    @Test
    void splitTendersGoToTheirOwnAccounts() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        SaleBody s = sale(List.of(new SaleBody.Payment("CASH", "6000", "", "10000"),
                new SaleBody.Payment("MOBILE_MONEY", "10240", "MOCK123", "10240")), "16240", null, 14000, 2240);
        postSale(tenant, feed(t, s));
        assertThat(balances(tenant)).containsEntry("CASH", 6000L).containsEntry("MOBILE_MONEY", 10240L)
                .containsEntry("SALES", -14000L).containsEntry("VAT_PAYABLE", -2240L);
        assertBooksBalance(tenant);
    }

    @Test
    void aSaleThatDoesNotAddUpIsPostedWithTheDifferenceInSuspenseAndRaised() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        // Customer paid 16000 against a net+tax of 16240: 240 short.
        postSale(tenant, feed(t, sale(List.of(new SaleBody.Payment("CASH", "16000", "", "16000")), "16000", null, 14000, 2240)));
        Map<String, Long> b = balances(tenant);
        assertThat(b).containsEntry("CASH", 16000L).containsEntry("SUSPENSE", 240L);
        assertBooksBalance(tenant);
        assertThat(admin("/v1/admin/exceptions", tenant).stream().map(m -> m.get("kind"))).contains("SALE_UNBALANCED");

        // Overpaid the other way lands as a credit in suspense.
        Till t2 = new Till(tenant);
        postSale(tenant, feed(t2, sale(List.of(new SaleBody.Payment("CASH", "17000", "", "17000")), "17000", null, 14000, 2240)));
        assertBooksBalance(tenant);
    }

    @Test
    void aSaleWithMalformedAmountsIsKeptButNotPosted() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        postSale(tenant, feed(t, sale(List.of(new SaleBody.Payment("CASH", "lots", "", "lots")), "lots", null, 100, 0)));
        assertThat(admin("/v1/admin/exceptions", tenant).stream().map(m -> m.get("kind"))).contains("SALE_UNPOSTABLE");
        assertThat(admin("/v1/admin/ledger/trial-balance", tenant)).isEmpty();
        assertThat(admin("/v1/admin/sales", tenant)).hasSize(1);
    }

    @Test
    void fiscalNumbersCountOnlyInsideTheTerminalsOwnLeaseAndOnlyOnce() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        Till other = new Till(tenant);
        lease(t);           // 1..100
        JsonNode otherLease = lease(other);   // 101..200

        postSale(tenant, feed(t, cashSale(1000, 0, "7")));        // inside t's lease
        postSale(tenant, feed(t, cashSale(1000, 0, "7")));        // the same number twice
        postSale(tenant, feed(t, cashSale(1000, 0, "150")));      // another terminal's lease
        postSale(tenant, feed(t, cashSale(1000, 0, "9999")));     // never leased

        var sales = admin("/v1/admin/sales", tenant);
        long accepted = sales.stream().filter(m -> Boolean.TRUE.equals(m.get("fiscalAccepted"))).count();
        assertThat(accepted).isEqualTo(1);
        var kinds = admin("/v1/admin/exceptions", tenant).stream().map(m -> (String) m.get("kind")).toList();
        assertThat(kinds).contains("FISCAL_DUPLICATE", "FISCAL_OUT_OF_LEASE");
        assertBooksBalance(tenant);   // the sales still happened and are in the books
        assertThat(otherLease.get("firstNumber").asText()).isEqualTo("101");
    }

    @Test
    void aVoidedNumberIsNotAcceptedIfASaleLaterClaimsIt() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        JsonNode l = lease(t);
        signedPost(t, "/v1/terminal/fiscal/leases/" + l.get("leaseId").asText() + "/return", Map.of("nextUnused", "10"))
                .andExpect(status().isOk());
        postSale(tenant, feed(t, cashSale(1000, 0, "50")));
        assertThat(admin("/v1/admin/exceptions", tenant).stream().map(m -> m.get("kind"))).contains("FISCAL_OUT_OF_LEASE");
    }

    /**
     * A terminal that spends a number while returning the lease that holds it must not end with
     * that number both used and voided. Raced many times because a missing lock shows up only
     * occasionally; with the lease row locked the invariant holds every time.
     */
    @Test
    void spendingANumberWhileReturningItsLeaseNeverLeavesItBothUsedAndVoided() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 40; round++) {
                String tenant = newTenant();
                Till t = new Till(tenant);
                JsonNode l = lease(t);
                String path = "/v1/terminal/fiscal/leases/" + l.get("leaseId").asText() + "/return";
                FeedEntry e = feed(t, cashSale(10000, 1600, "5"));
                java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
                Future<?> sale = pool.submit(() -> {
                    go.await();
                    return postSale(tenant, e);
                });
                Future<Integer> back = pool.submit(() -> {
                    go.await();
                    return signedPost(t, path, Map.of("nextUnused", "3")).andReturn().getResponse().getStatus();
                });
                go.countDown();
                sale.get();
                int returned = back.get();
                assertThat(returned).isIn(200, 409);
                try (var c = owner.getConnection(); var st = c.createStatement();
                        var rs = st.executeQuery("SELECT count(*) FROM sale s JOIN fiscal_void v ON v.tenant_id = s.tenant_id"
                                + " WHERE s.tenant_id = '" + tenant + "' AND s.fiscal_accepted"
                                + " AND s.fiscal_number BETWEEN v.from_number AND v.to_number")) {
                    rs.next();
                    assertThat(rs.getLong(1)).as("round %d: number is both spent and voided", round).isZero();
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void theSameEntryPostedFromTwoThreadsIsPostedOnce() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        FeedEntry e = feed(t, cashSale(14000, 2240, null));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger posted = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            fs.add(pool.submit(() -> {
                if (postSale(tenant, e) == SaleIngestService.Outcome.POSTED) {
                    posted.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        assertThat(posted.get()).isEqualTo(1);
        assertThat(balances(tenant)).containsEntry("CASH", 16240L);
        assertBooksBalance(tenant);
    }

    @Test
    void theFeedMustArriveInOrder() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        feed(t, cashSale(100, 0, null));           // sequence 1, never delivered
        FeedEntry second = feed(t, cashSale(100, 0, null));
        assertThatThrownBy(() -> postSale(tenant, second)).hasMessageContaining("skipped");
    }

    // ------------------------------------------------ invariants in the database

    @Test
    void theDatabaseItselfRefusesAnUnbalancedTransactionAndAnyEditOfThePast() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        postSale(tenant, feed(t, cashSale(14000, 2240, null)));
        var tx = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(owner));
        var jdbc = new JdbcTemplate(owner);

        // A one-sided transaction cannot commit, even for the owner.
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            bind(jdbc, tenant);
            long id = jdbc.queryForObject("""
                    INSERT INTO ledger_txn (tenant_id, kind, currency, occurred_at) VALUES (?, 'SALE', 'KES', now())
                    RETURNING id""", Long.class, tenant);
            jdbc.update("INSERT INTO posting (txn_id, tenant_id, account_code, currency, debit_minor) VALUES (?, ?, 'CASH', 'KES', 100)",
                    id, tenant);
            jdbc.update("INSERT INTO posting (txn_id, tenant_id, account_code, currency, credit_minor) VALUES (?, ?, 'SALES', 'KES', 99)",
                    id, tenant);
        })).rootCause().hasMessageContaining("unbalanced");

        // A transaction with no postings at all cannot commit either.
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            bind(jdbc, tenant);
            jdbc.update("INSERT INTO ledger_txn (tenant_id, kind, currency, occurred_at) VALUES (?, 'SALE', 'KES', now())", tenant);
        })).rootCause().hasMessageContaining("postings");

        // History is append-only.
        for (String statement : List.of("UPDATE posting SET debit_minor = 1", "DELETE FROM posting", "DELETE FROM ledger_txn",
                "UPDATE sale SET total_minor = 1", "DELETE FROM sale")) {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                bind(jdbc, tenant);
                jdbc.execute(statement);
            })).as(statement).rootCause().hasMessageContaining("append-only");
        }
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE posting")).rootCause().hasMessageContaining("append-only");
    }

    @Test
    void rowLevelSecurityHidesOneTenantsBooksFromAnother() throws Exception {
        String a = newTenant();
        String b = newTenant();
        postSale(a, feed(new Till(a), cashSale(14000, 2240, null)));
        assertThat(balances(b)).isEmpty();
        assertThat(admin("/v1/admin/sales", b)).isEmpty();

        // Straight to the table as the application role: with no tenant bound, nothing is visible.
        var tx = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(app));
        Long visible = tx.execute(s -> new JdbcTemplate(app).queryForObject("SELECT count(*) FROM posting", Long.class));
        assertThat(visible).isZero();
        // and the application role cannot write another tenant's rows even deliberately.
        assertThatThrownBy(() -> TenantContext.with(b, () -> tx.execute(s ->
                new JdbcTemplate(app).update("INSERT INTO account (tenant_id, code, name, kind) VALUES (?, 'X', 'x', 'ASSET')", a))))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void adminAndInternalEndpointsRefuseTheWrongOrMissingToken() throws Exception {
        mvc.perform(get("/v1/admin/sales").header("X-Mara-Tenant", "x")).andExpect(status().isUnauthorized());
        // a valid credential that lacks the scope is refused as forbidden, not as unknown
        mvc.perform(get("/v1/admin/sales").header("Authorization", "Bearer " + INTERNAL).header("X-Mara-Tenant", "x"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/ingest/run").header("Authorization", "Bearer nope")).andExpect(status().isUnauthorized());
        // a path nobody has decided who may call is closed, even to a credential with every scope
        mvc.perform(get("/v1/admin/../internal/unlisted").header("Authorization", "Bearer " + ADMIN)).andExpect(status().is4xxClientError());
    }

    private static void bind(JdbcTemplate jdbc, String tenant) {
        jdbc.queryForObject("SELECT set_config('mara.tenant_id', ?, true)", String.class, tenant);
    }

    // ---------------------------------------------------------------- poller

    @Test
    void theFeedPollerPostsWhatSyncServicePublishesAndOnlyOnce() throws Exception {
        String tenant = newTenant();
        Till t = new Till(tenant);
        FEED.put(t.id, new ArrayList<>(List.of(feed(t, cashSale(10000, 1600, null)), feed(t, cashSale(5000, 0, null)))));
        int first = poller.pollOnce();
        assertThat(first).isGreaterThanOrEqualTo(2);
        assertThat(balances(tenant)).containsEntry("CASH", 11600L + 5000L);
        assertThat(poller.pollOnce()).isZero();
        assertBooksBalance(tenant);
    }

    private static HttpServer startFakeSync() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ObjectMapper m = new ObjectMapper();
            server.createContext("/v1/internal/chains", ex -> {
                List<Map<String, Object>> heads = new ArrayList<>();
                FEED.forEach((terminal, entries) -> {
                    if (!entries.isEmpty()) {
                        heads.add(Map.of("terminalId", terminal, "tenantId", entries.get(0).tenantId(),
                                "lastSequence", entries.get(entries.size() - 1).sequence()));
                    }
                });
                String after = ex.getRequestURI().getQuery().replaceAll(".*after=", "");
                heads.removeIf(h -> ((String) h.get("terminalId")).compareTo(after) <= 0);
                heads.sort((x, y) -> ((String) x.get("terminalId")).compareTo((String) y.get("terminalId")));
                reply(ex, m.writeValueAsBytes(heads));
            });
            server.createContext("/v1/internal/terminals/", ex -> {
                String[] parts = ex.getRequestURI().getPath().split("/");
                String terminal = parts[4];
                long after = Long.parseLong(ex.getRequestURI().getQuery().replaceAll(".*after=", ""));
                List<Map<String, Object>> out = new ArrayList<>();
                for (FeedEntry e : FEED.getOrDefault(terminal, List.of())) {
                    if (e.sequence() > after) {
                        out.add(Map.of("terminalId", e.terminalId(), "tenantId", e.tenantId(), "sequence", e.sequence(),
                                "epochSecond", e.epochSecond(), "nano", e.nano(), "sale", e.sale()));
                    }
                }
                reply(ex, m.writeValueAsBytes(out));
            });
            server.start();
            return server;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, byte[] body) throws java.io.IOException {
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }
}
