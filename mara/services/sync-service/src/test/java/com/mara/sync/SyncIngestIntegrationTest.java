package com.mara.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.kit.auth.TerminalDirectory;
import com.mara.kit.auth.TerminalRecord;
import com.mara.platform.sale.SaleBody;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

/**
 * The real application against a real PostgreSQL, through HTTP, with terminals that keep a
 * real hash chain and sign it. Needs {@code -Dmara.test.jdbc.url}; skipped without it.
 * Each test uses a fresh terminal and tenant, because the journal is append-only by design.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SyncIngestIntegrationTest {

    private static final String JDBC_URL = System.getProperty("mara.test.jdbc.url");
    private static final String INTERNAL = "test-internal-token-0123456789-abc";
    private static final String ADMIN = "test-admin-token-0123456789-abcdef";

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
        r.add("mara.internal.token", () -> INTERNAL);
        r.add("mara.admin.token", () -> ADMIN);
    }

    /** Stands in for identity-service: the enrolled terminals, by id. */
    @TestConfiguration
    static class Directory {
        static final Map<String, TerminalRecord> TERMINALS = new ConcurrentHashMap<>();

        @Bean
        @Primary
        TerminalDirectory testDirectory() {
            return id -> Optional.ofNullable(TERMINALS.get(id));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate app;
    @Autowired @Qualifier("ownerDataSource") DataSource owner;

    private TestTerminal enrol(String tenant, String status) {
        TestTerminal t = new TestTerminal(tenant);
        Directory.TERMINALS.put(t.id, new TerminalRecord(t.id, tenant, "BR-1", t.publicKey(), status));
        return t;
    }

    private TestTerminal enrol() {
        return enrol("TEN-" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE");
    }

    private ResultActions signed(TestTerminal t, String method, String path, Object body, long at) throws Exception {
        byte[] bytes = body == null ? new byte[0] : json.writeValueAsBytes(body);
        MockHttpServletRequestBuilder b = method.equals("GET") ? get(path) : post(path);
        t.signedHeaders(method, path, bytes, at).forEach(b::header);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(bytes);
        }
        return mvc.perform(b);
    }

    private ResultActions upload(TestTerminal t, List<Map<String, Object>> entries) throws Exception {
        return signed(t, "POST", "/v1/terminal/sync/journal", Map.of("entries", entries), Instant.now().getEpochSecond());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private List<Map<String, Object>> exceptionsOf(TestTerminal t) throws Exception {
        String body = mvc.perform(get("/v1/admin/exceptions?open=false")
                        .header("Authorization", "Bearer " + ADMIN).header("X-Mara-Tenant", t.tenantId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    }

    // -------------------------------------------------------------------------

    @Test
    void anUploadIsVerifiedChainedAndStoredAndARetryIsHarmless() throws Exception {
        TestTerminal t = enrol();
        var entries = List.of(t.record(TestTerminal.sale(7000, 2, 1600, null)),
                t.record(TestTerminal.sale(12999, 1, 1600, null)),
                t.record(TestTerminal.sale(500, 10, 0, null)));

        JsonNode first = body(upload(t, entries).andExpect(status().isOk()));
        assertThat(first.get("acceptedThrough").asLong()).isEqualTo(3);
        assertThat(first.get("accepted").asInt()).isEqualTo(3);
        assertThat(first.get("gap").isNull()).isTrue();
        assertThat(first.get("refused")).isEmpty();

        JsonNode retry = body(upload(t, entries).andExpect(status().isOk()));
        assertThat(retry.get("accepted").asInt()).isZero();
        assertThat(retry.get("duplicates").asInt()).isEqualTo(3);
        assertThat(retry.get("acceptedThrough").asLong()).isEqualTo(3);

        JsonNode status = body(signed(t, "GET", "/v1/terminal/sync/status", null, Instant.now().getEpochSecond())
                .andExpect(status().isOk()));
        assertThat(status.get("lastSequence").asLong()).isEqualTo(3);
        assertThat(status.get("headDigest").asText()).isEqualTo(entries.get(2).get("digest"));
        assertThat(status.get("openExceptions").asInt()).isZero();
    }

    @Test
    void aSaleEditedAfterSigningIsRefusedAndTheEntriesBehindItAreHeldAtAGap() throws Exception {
        TestTerminal t = enrol();
        var e1 = t.record(TestTerminal.sale(7000, 2, 1600, null));
        var e2 = t.record(TestTerminal.sale(12999, 1, 1600, null));
        var e3 = t.record(TestTerminal.sale(500, 10, 0, null));
        // Someone lowers a price after the fact but keeps the signature.
        SaleBody original = (SaleBody) e2.get("sale");
        var line = original.lines().get(0);
        SaleBody edited = new SaleBody(original.version(), original.currency(),
                List.of(new SaleBody.Line(line.sku(), line.name(), line.qty(), "1", line.taxBp(), line.netMinor(), line.taxMinor())),
                original.payments(), original.totalMinor(), original.fiscal(), null);
        e2.put("sale", edited);

        JsonNode r = body(upload(t, List.of(e1, e2, e3)).andExpect(status().isOk()));
        assertThat(r.get("acceptedThrough").asLong()).isEqualTo(1);
        assertThat(r.get("refused").get(0).get("reason").asText()).isEqualTo("BAD_BODY_DIGEST");
        assertThat(r.get("gap").get("from").asLong()).isEqualTo(2);

        var kinds = exceptionsOf(t).stream().map(m -> (String) m.get("kind")).toList();
        assertThat(kinds).contains("BAD_BODY_DIGEST", "GAP");
    }

    @Test
    void aDeletedSaleIsAGapAndHealsWhenTheMissingEntryArrives() throws Exception {
        TestTerminal t = enrol();
        var e1 = t.record(TestTerminal.sale(7000, 2, 1600, null));
        var e2 = t.record(TestTerminal.sale(12999, 1, 1600, null));
        var e3 = t.record(TestTerminal.sale(500, 10, 0, null));

        JsonNode held = body(upload(t, List.of(e1, e3)).andExpect(status().isOk()));
        assertThat(held.get("acceptedThrough").asLong()).isEqualTo(1);
        assertThat(held.get("gap").get("from").asLong()).isEqualTo(2);
        assertThat(held.get("gap").get("to").asLong()).isEqualTo(2);
        JsonNode status = body(signed(t, "GET", "/v1/terminal/sync/status", null, Instant.now().getEpochSecond()));
        assertThat(status.get("heldAtGap").asBoolean()).isTrue();

        JsonNode healed = body(upload(t, List.of(e2, e3)).andExpect(status().isOk()));
        assertThat(healed.get("acceptedThrough").asLong()).isEqualTo(3);
        assertThat(healed.get("gap").isNull()).isTrue();
        var open = exceptionsOf(t).stream().filter(m -> m.get("resolvedAt") == null).toList();
        assertThat(open).isEmpty();
    }

    @Test
    void aRewrittenSequenceIsAFork_andTheFirstCopyStandsUnchanged() throws Exception {
        TestTerminal t = enrol();
        var e1 = t.record(TestTerminal.sale(7000, 2, 1600, null));
        var e2 = t.record(TestTerminal.sale(12999, 1, 1600, null));
        upload(t, List.of(e1, e2)).andExpect(status().isOk());

        // The same sequence 2, a different (validly signed) sale: history rewritten after upload.
        var rewritten = t.build(2, 1_760_000_010L, TestTerminal.sale(100, 1, 0, null),
                java.util.HexFormat.of().parseHex((String) e1.get("digest")));
        JsonNode r = body(upload(t, List.of(rewritten)).andExpect(status().isOk()));
        assertThat(r.get("accepted").asInt()).isZero();
        assertThat(r.get("refused").get(0).get("reason").asText()).isEqualTo("FORKED_SEQUENCE");
        assertThat(r.get("acceptedThrough").asLong()).isEqualTo(2);
        assertThat(exceptionsOf(t).stream().map(m -> m.get("kind"))).contains("FORKED_SEQUENCE");
    }

    @Test
    void aValidlySignedSaleWithBadArithmeticIsStoredAndFlagged() throws Exception {
        TestTerminal t = enrol();
        SaleBody good = TestTerminal.sale(7000, 2, 1600, null);
        // The till claims a total 100 lower than net + tax and a payment that matches the lie.
        SaleBody off = new SaleBody(good.version(), good.currency(), good.lines(),
                List.of(new SaleBody.Payment("CASH", "16140", "", "16140")), "16140", good.fiscal(), null);
        var e1 = t.record(off);

        JsonNode r = body(upload(t, List.of(e1)).andExpect(status().isOk()));
        assertThat(r.get("accepted").asInt()).isEqualTo(1);
        assertThat(r.get("flagged").get(0).asText()).contains("TOTAL_MISMATCH");
        assertThat(exceptionsOf(t).stream().map(m -> m.get("kind"))).contains("SALE_INCONSISTENT");
    }

    @Test
    void anEntryNamingAnotherTerminalIsRefused() throws Exception {
        TestTerminal t = enrol();
        TestTerminal other = enrol(t.tenantId, "ACTIVE");
        var theirs = other.record(TestTerminal.sale(100, 1, 0, null));
        JsonNode r = body(upload(t, List.of(theirs)).andExpect(status().isOk()));
        assertThat(r.get("accepted").asInt()).isZero();
        assertThat(r.get("refused").get(0).get("reason").asText()).isEqualTo("FOREIGN_ENTRY");
    }

    @Test
    void requestsAreAuthenticatedAsTheTerminalOrRefusedIdentically() throws Exception {
        TestTerminal t = enrol();
        var entries = List.of(t.record(TestTerminal.sale(7000, 2, 1600, null)));
        byte[] body = json.writeValueAsBytes(Map.of("entries", entries));
        long now = Instant.now().getEpochSecond();

        mvc.perform(post("/v1/terminal/sync/journal").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // stale
        signed(t, "POST", "/v1/terminal/sync/journal", Map.of("entries", entries), now - 3600).andExpect(status().isUnauthorized());
        // signed for a different body than the one sent
        MockHttpServletRequestBuilder swapped = post("/v1/terminal/sync/journal").contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":[]}".getBytes(StandardCharsets.UTF_8));
        t.signedHeaders("POST", "/v1/terminal/sync/journal", body, now).forEach(swapped::header);
        mvc.perform(swapped).andExpect(status().isUnauthorized());
        // signed with another terminal's key
        TestTerminal impostor = new TestTerminal(t.tenantId);
        MockHttpServletRequestBuilder forged = post("/v1/terminal/sync/journal").contentType(MediaType.APPLICATION_JSON).content(body);
        Map<String, String> h = impostor.signedHeaders("POST", "/v1/terminal/sync/journal", body, now);
        forged.header("X-Mara-Terminal", t.id).header("X-Mara-Timestamp", String.valueOf(now))
                .header("X-Mara-Signature", h.get("X-Mara-Signature"));
        mvc.perform(forged).andExpect(status().isUnauthorized());
        // unknown terminal
        TestTerminal ghost = new TestTerminal("TEN-x");
        signed(ghost, "POST", "/v1/terminal/sync/journal", Map.of("entries", entries), now).andExpect(status().isUnauthorized());
        // a suspended terminal is no longer believed
        TestTerminal suspended = enrol("TEN-" + UUID.randomUUID().toString().substring(0, 8), "SUSPENDED");
        signed(suspended, "POST", "/v1/terminal/sync/journal",
                Map.of("entries", List.of(suspended.record(TestTerminal.sale(1, 1, 0, null)))), now)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantsCannotSeeEachOthersJournalsEvenThroughTheInternalFeed() throws Exception {
        TestTerminal a = enrol();
        TestTerminal b = enrol();
        upload(a, List.of(a.record(TestTerminal.sale(7000, 2, 1600, null)))).andExpect(status().isOk());
        upload(b, List.of(b.record(TestTerminal.sale(300, 1, 0, null)))).andExpect(status().isOk());

        String path = "/v1/internal/terminals/" + a.id + "/entries?after=0";
        // With its own tenant: the entry. With another tenant's: nothing. Without the token: refused.
        JsonNode own = json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + INTERNAL)
                .header("X-Mara-Tenant", a.tenantId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(own).hasSize(1);
        JsonNode foreign = json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + INTERNAL)
                .header("X-Mara-Tenant", b.tenantId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(foreign).isEmpty();
        mvc.perform(get(path).header("X-Mara-Tenant", a.tenantId)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + ADMIN).header("X-Mara-Tenant", a.tenantId))
                .andExpect(status().isUnauthorized());

        // The chain list is the one cross-tenant read, and it names terminals and counters only.
        JsonNode heads = json.readTree(mvc.perform(get("/v1/internal/chains").header("Authorization", "Bearer " + INTERNAL))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(heads.toString()).contains(a.id).contains(b.id).doesNotContain("sale");
    }

    @Test
    void theJournalIsAppendOnlyEvenForItsOwner() throws Exception {
        TestTerminal t = enrol();
        upload(t, List.of(t.record(TestTerminal.sale(7000, 2, 1600, null)))).andExpect(status().isOk());

        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(owner));
        var jdbc = new JdbcTemplate(owner);
        // Even as the owner, with the row visible, UPDATE and DELETE are refused by the trigger.
        for (String statement : List.of("UPDATE journal_entry SET nano = 1", "DELETE FROM journal_entry")) {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('mara.tenant_id', ?, true)", String.class, t.tenantId);
                jdbc.execute(statement);
            }), statement);
        }
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.execute("TRUNCATE journal_entry"));
        // (The test database's owner is a superuser, which bypasses RLS, so name the tenant.)
        Long rows = jdbc.queryForObject("SELECT count(*) FROM journal_entry WHERE tenant_id = ?", Long.class, t.tenantId);
        assertThat(rows).isEqualTo(1L);
    }
}
