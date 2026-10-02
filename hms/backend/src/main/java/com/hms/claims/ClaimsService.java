package com.hms.claims;

import static com.hms.claims.ClaimModels.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Madai: claims readiness. Assembles a claim from the closed encounter, its diagnoses and the issued
 * invoice, checks it against readiness rules, and can hand it to the configured adapter.
 *
 * What this is NOT: the rules below are HMS's own hygiene checks for a claim to be complete and
 * internally consistent. They are not the SHA or DHA rule set, the bundle is not a DHA FHIR
 * resource, and no submission reaches a payer. Those need the DHA eClaims specification,
 * credentials and certification, none of which exist here.
 */
@Service
public class ClaimsService {

    static final String DISCLAIMER = "Readiness checks are HMS's own and are not the SHA/DHA rule set. The bundle is an internal snapshot, not a DHA eClaims resource. "
            + "Nothing here has been transmitted to or verified by SHA or DHA.";
    private static final Pattern ICD11 = Pattern.compile("^[0-9A-Z]{4}(\\.[0-9A-Z]{1,2})?$");

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;
    private final ClaimsGateway gateway;
    private final ObjectMapper canonical;

    public ClaimsService(JdbcClient jdbc, AuditService audit, PatientAccess patients, ClaimsGateway gateway, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
        this.gateway = gateway;
        this.canonical = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    // ---- assembly ----------------------------------------------------------------------------

    @Transactional
    public Claim assemble(AssembleInput in) {
        TenantContext.Tenant t = TenantContext.require();
        var inv = jdbc.sql("SELECT facility_id, patient_id, encounter_id, payer_type FROM invoices WHERE org_id = ? AND id = ?").params(t.orgId(), in.invoiceId())
                .query((rs, n) -> new Object[] {rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getObject("encounter_id", UUID.class), rs.getString("payer_type")})
                .optional().orElseThrow(() -> ApiException.notFound("Invoice"));
        UUID facilityId = (UUID) inv[0];
        UUID patientId = (UUID) inv[1];
        t.requireFacility(facilityId);
        patients.require(patientId);
        if (inv[2] == null) {
            throw ApiException.badRequest("no_encounter", "A claim is built from an encounter; this invoice is not linked to one.");
        }
        if ("CASH".equals(inv[3])) {
            throw ApiException.badRequest("cash_invoice", "A cash invoice has no payer to claim from.");
        }
        Built built = build(in.invoiceId());
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO claims (org_id, facility_id, patient_id, encounter_id, invoice_id, claim_number, payer_type, status, total, bundle, bundle_hash, assembled_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?) RETURNING id""")
                    .params(t.orgId(), facilityId, patientId, inv[2], in.invoiceId(), number(t, facilityId), inv[3], statusOf(built.issues), built.total, built.json, built.hash, t.practitionerId())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("claim_exists", "That invoice already has a live claim. Reassemble it, or withdraw it first.");
        }
        storeIssues(t, id, built.issues);
        audit.record("claim.assemble", "claim", id, facilityId, null, Map.of("invoice", in.invoiceId().toString(), "errors", count(built.issues, "ERROR"), "warnings", count(built.issues, "WARNING")));
        return load(id, true);
    }

    /** Re-reads the live record and re-checks. The only way a claim's content changes: never edited by hand. */
    @Transactional
    public Claim reassemble(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Claim c = lock(id);
        if (!List.of("DRAFT", "NEEDS_ATTENTION", "READY").contains(c.status())) {
            throw ApiException.conflict("claim_locked", "A claim that has been submitted or withdrawn cannot be rebuilt (this one is " + c.status() + ").");
        }
        Built built = build(c.invoiceId());
        jdbc.sql("UPDATE claims SET status = ?, total = ?, bundle = ?::jsonb, bundle_hash = ?, assembled_by = ?, assembled_at = now(), version = version + 1 WHERE org_id = ? AND id = ?")
                .params(statusOf(built.issues), built.total, built.json, built.hash, t.practitionerId(), t.orgId(), id).update();
        jdbc.sql("DELETE FROM claim_issues WHERE org_id = ? AND claim_id = ?").params(t.orgId(), id).update();
        storeIssues(t, id, built.issues);
        audit.record("claim.reassemble", "claim", id, c.facilityId(), null, Map.of("errors", count(built.issues, "ERROR"), "warnings", count(built.issues, "WARNING")));
        return load(id, true);
    }

    /** noRollbackFor: when the record changed, the rebuilt claim must be kept even though the caller gets a 409. */
    @Transactional(noRollbackFor = ApiException.class)
    public Claim submit(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Claim c = lock(id);
        if (!"READY".equals(c.status())) {
            throw ApiException.conflict("not_ready", "Only a claim with no errors (status READY) can be submitted; this one is " + c.status() + ".");
        }
        // Re-check against the live record at the moment of submission, so a change since assembly is caught.
        Built fresh = build(c.invoiceId());
        if (count(fresh.issues, "ERROR") > 0 || !java.util.Arrays.equals(fresh.hash, hashOf(id))) {
            jdbc.sql("UPDATE claims SET status = ?, total = ?, bundle = ?::jsonb, bundle_hash = ?, assembled_at = now(), version = version + 1 WHERE org_id = ? AND id = ?")
                    .params(statusOf(fresh.issues), fresh.total, fresh.json, fresh.hash, t.orgId(), id).update();
            jdbc.sql("DELETE FROM claim_issues WHERE org_id = ? AND claim_id = ?").params(t.orgId(), id).update();
            storeIssues(t, id, fresh.issues);
            throw ApiException.conflict("claim_changed", "The record changed since this claim was assembled. It has been rebuilt; review it and submit again.");
        }
        ClaimsGateway.Outcome out = gateway.submit(c.claimNumber(), fresh.bundle);
        jdbc.sql("""
                INSERT INTO claim_submissions (org_id, claim_id, adapter, verified, sent, outcome, detail, bundle_hash, submitted_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(t.orgId(), id, out.adapter(), out.verified(), out.sent(), out.outcome(), out.detail(), fresh.hash, t.practitionerId()).update();
        // Only a verified adapter that actually sent the claim may move it to SUBMITTED. The stub never does.
        String status = out.verified() && out.sent() ? "SUBMITTED" : "SUBMISSION_STUBBED";
        jdbc.sql("UPDATE claims SET status = ?, version = version + 1 WHERE org_id = ? AND id = ?").params(status, t.orgId(), id).update();
        audit.record("claim.submit", "claim", id, c.facilityId(), null, Map.of("adapter", out.adapter(), "verified", out.verified(), "sent", out.sent()));
        return load(id, true);
    }

    @Transactional
    public Claim withdraw(UUID id, WithdrawInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Claim c = lock(id);
        if (List.of("WITHDRAWN", "SUBMITTED", "ACCEPTED", "PAID").contains(c.status())) {
            throw ApiException.conflict("not_withdrawable", "A claim that is " + c.status() + " cannot be withdrawn here.");
        }
        jdbc.sql("UPDATE claims SET status = 'WITHDRAWN', withdraw_reason = ?, version = version + 1 WHERE org_id = ? AND id = ?").params(in.reason().trim(), t.orgId(), id).update();
        audit.record("claim.withdraw", "claim", id, c.facilityId(), in.reason().trim(), Map.of());
        return load(id, true);
    }

    // ---- reading -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Claim open(UUID id) {
        Claim c = load(id, true);
        TenantContext.require().requireFacility(c.facilityId());
        patients.require(c.patientId());
        return c;
    }

    @Transactional(readOnly = true)
    public Slice<Row> list(UUID facilityId, String status, UUID patientId, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(t.orgId()));
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.claim_number, c.patient_id, pt.given_name || ' ' || pt.family_name AS patient_name, c.status, c.total, c.assembled_at, c.created_at,
                       (SELECT count(*) FROM claim_issues i WHERE i.org_id = c.org_id AND i.claim_id = c.id AND i.severity = 'ERROR') AS errors,
                       (SELECT count(*) FROM claim_issues i WHERE i.org_id = c.org_id AND i.claim_id = c.id AND i.severity = 'WARNING') AS warnings
                  FROM claims c JOIN patients pt ON pt.org_id = c.org_id AND pt.id = c.patient_id WHERE c.org_id = ?""");
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND c.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND c.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND c.status = ?");
            p.add(status);
        }
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND c.patient_id = ?");
            p.add(patientId);
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (c.created_at, c.id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY c.created_at DESC, c.id DESC LIMIT ?");
        p.add(size + 1);
        List<Object[]> rows = jdbc.sql(sql.toString()).params(p.toArray())
                .query((rs, n) -> new Object[] {new Row(rs.getObject("id", UUID.class), rs.getString("claim_number"), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                        rs.getString("status"), rs.getBigDecimal("total"), rs.getInt("errors"), rs.getInt("warnings"), rs.getObject("assembled_at", OffsetDateTime.class).toInstant()),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant().toString()}).list();
        boolean more = rows.size() > size;
        List<Object[]> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", (String) page.get(page.size() - 1)[1], "i", ((Row) page.get(page.size() - 1)[0]).id().toString())) : null;
        return new Slice<>(page.stream().map(r -> (Row) r[0]).toList(), next);
    }

    /** Where the claims are and which rules are failing them: the work list for the claims office. */
    @Transactional(readOnly = true)
    public Summary summary(UUID facilityId) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.sql("SELECT status, count(*) AS n FROM claims WHERE org_id = ? AND facility_id = ? GROUP BY status ORDER BY status").params(t.orgId(), facilityId)
                .query((rs, n) -> Map.entry(rs.getString("status"), rs.getLong("n"))).list().forEach(e -> byStatus.put(e.getKey(), e.getValue()));
        List<RuleCount> top = jdbc.sql("""
                SELECT i.rule_code, i.severity, count(DISTINCT i.claim_id) AS n FROM claim_issues i JOIN claims c ON c.org_id = i.org_id AND c.id = i.claim_id
                 WHERE i.org_id = ? AND c.facility_id = ? AND c.status IN ('DRAFT', 'NEEDS_ATTENTION', 'READY') GROUP BY i.rule_code, i.severity ORDER BY n DESC, i.rule_code LIMIT 20""")
                .params(t.orgId(), facilityId).query((rs, n) -> new RuleCount(rs.getString("rule_code"), rs.getString("severity"), rs.getLong("n"))).list();
        return new Summary(byStatus, top);
    }

    // ---- building and checking ---------------------------------------------------------------

    private record Built(Map<String, Object> bundle, String json, byte[] hash, BigDecimal total, List<Issue> issues) {}

    private Built build(UUID invoiceId) {
        TenantContext.Tenant t = TenantContext.require();
        List<Issue> issues = new ArrayList<>();
        var inv = jdbc.sql("SELECT facility_id, patient_id, encounter_id, status, payer_type, payer_name, total, issued_at, invoice_number FROM invoices WHERE org_id = ? AND id = ?")
                .params(t.orgId(), invoiceId).query((rs, n) -> {
                    Map<String, Object> m = new TreeMap<>();
                    m.put("facilityId", rs.getObject("facility_id", UUID.class));
                    m.put("patientId", rs.getObject("patient_id", UUID.class));
                    m.put("encounterId", rs.getObject("encounter_id", UUID.class));
                    m.put("status", rs.getString("status"));
                    m.put("payerType", rs.getString("payer_type"));
                    m.put("payerName", rs.getString("payer_name"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("issuedAt", rs.getObject("issued_at", OffsetDateTime.class));
                    m.put("number", rs.getString("invoice_number"));
                    return m;
                }).optional().orElseThrow(() -> ApiException.notFound("Invoice"));
        UUID patientId = (UUID) inv.get("patientId");
        UUID encounterId = (UUID) inv.get("encounterId");
        UUID facilityId = (UUID) inv.get("facilityId");

        var fac = jdbc.sql("SELECT name, mfl_code, keph_level FROM facilities WHERE org_id = ? AND id = ?").params(t.orgId(), facilityId)
                .query((rs, n) -> mapOf("name", rs.getString("name"), "mflCode", rs.getString("mfl_code"), "kephLevel", rs.getObject("keph_level"))).single();
        var pat = jdbc.sql("SELECT given_name, family_name, sex, birth_date FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), patientId)
                .query((rs, n) -> mapOf("givenName", rs.getString("given_name"), "familyName", rs.getString("family_name"), "sex", rs.getString("sex"),
                        "birthDate", rs.getObject("birth_date", java.time.LocalDate.class))).single();
        Map<String, String> ids = new TreeMap<>();
        jdbc.sql("SELECT system, value FROM patient_identifiers WHERE org_id = ? AND patient_id = ? AND system <> 'MRN'").params(t.orgId(), patientId)
                .query((rs, n) -> Map.entry(rs.getString("system"), rs.getString("value"))).list().forEach(e -> ids.put(e.getKey(), e.getValue()));
        pat.put("identifiers", ids);
        var enc = jdbc.sql("""
                SELECT e.encounter_type, e.status, e.started_at, e.ended_at, pr.full_name, pr.licence_body, pr.licence_no
                  FROM encounters e LEFT JOIN practitioners pr ON pr.org_id = e.org_id AND pr.id = e.attending_id WHERE e.org_id = ? AND e.id = ?""").params(t.orgId(), encounterId)
                .query((rs, n) -> mapOf("type", rs.getString("encounter_type"), "status", rs.getString("status"), "startedAt", rs.getObject("started_at", OffsetDateTime.class),
                        "endedAt", rs.getObject("ended_at", OffsetDateTime.class), "attending", mapOf("name", rs.getString("full_name"), "licenceBody", rs.getString("licence_body"),
                                "licenceNo", rs.getString("licence_no")))).single();
        List<Map<String, Object>> dx = jdbc.sql("SELECT icd11_code, title, kind, certainty FROM diagnoses WHERE org_id = ? AND encounter_id = ? AND certainty <> 'RULED_OUT' ORDER BY (kind = 'PRIMARY') DESC, created_at")
                .params(t.orgId(), encounterId).query((rs, n) -> mapOf("code", rs.getString("icd11_code"), "title", rs.getString("title"), "kind", rs.getString("kind"),
                        "certainty", rs.getString("certainty"))).list();
        List<Map<String, Object>> lines = jdbc.sql("SELECT description, source_type, quantity, unit_price, line_total FROM invoice_lines WHERE org_id = ? AND invoice_id = ? ORDER BY description, id")
                .params(t.orgId(), invoiceId).query((rs, n) -> mapOf("description", rs.getString("description"), "source", rs.getString("source_type"), "quantity", rs.getBigDecimal("quantity"),
                        "unitPrice", rs.getBigDecimal("unit_price"), "total", rs.getBigDecimal("line_total"))).list();

        // ---- rules -------------------------------------------------------------------------
        if (!"SHA".equals(inv.get("payerType"))) {
            issues.add(new Issue("PAYER_NOT_SHA", "WARNING", "invoice.payerType", "The invoice payer is " + inv.get("payerType") + "; the rules here are written with SHA claims in mind."));
        }
        if (!ids.containsKey("SHA_NUMBER")) {
            issues.add(new Issue("PATIENT_SHA_NUMBER_MISSING", "ERROR", "patient.identifiers.SHA_NUMBER", "The patient has no SHA number recorded."));
        }
        if (!ids.containsKey("NATIONAL_ID") && !ids.containsKey("BIRTH_CERTIFICATE") && !ids.containsKey("PASSPORT") && !ids.containsKey("ALIEN_ID")) {
            issues.add(new Issue("PATIENT_ID_DOCUMENT_MISSING", "WARNING", "patient.identifiers", "No national ID, birth certificate, passport or alien ID is recorded."));
        }
        if (fac.get("mflCode") == null) {
            issues.add(new Issue("FACILITY_MFL_MISSING", "ERROR", "facility.mflCode", "The facility has no Master Health Facility List code."));
        }
        if (!"CLOSED".equals(enc.get("status"))) {
            issues.add(new Issue("ENCOUNTER_OPEN", "ERROR", "encounter.status", "The encounter is still open; close it before claiming."));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> attending = (Map<String, Object>) enc.get("attending");
        if (attending.get("licenceNo") == null) {
            issues.add(new Issue("ATTENDING_LICENCE_MISSING", "ERROR", "encounter.attending.licenceNo", "The attending clinician has no professional licence number on file."));
        }
        boolean hasPrimary = dx.stream().anyMatch(d -> "PRIMARY".equals(d.get("kind")));
        if (!hasPrimary) {
            issues.add(new Issue("PRIMARY_DIAGNOSIS_MISSING", "ERROR", "diagnoses", "The encounter has no primary diagnosis."));
        }
        for (Map<String, Object> d : dx) {
            if (!ICD11.matcher((String) d.get("code")).matches()) {
                issues.add(new Issue("DIAGNOSIS_CODE_FORMAT", "ERROR", "diagnoses." + d.get("code"), "'" + d.get("code") + "' is not shaped like an ICD-11 code."));
            }
            if ("PRIMARY".equals(d.get("kind")) && "PROVISIONAL".equals(d.get("certainty"))) {
                issues.add(new Issue("PRIMARY_DIAGNOSIS_PROVISIONAL", "WARNING", "diagnoses." + d.get("code"), "The primary diagnosis is still provisional."));
            }
        }
        if ("DRAFT".equals(inv.get("status")) || "VOID".equals(inv.get("status"))) {
            issues.add(new Issue("INVOICE_NOT_ISSUED", "ERROR", "invoice.status", "The invoice is " + inv.get("status") + "; claim from an issued invoice."));
        }
        if (lines.isEmpty()) {
            issues.add(new Issue("NO_CHARGES", "ERROR", "lines", "The invoice has no charge lines."));
        }
        BigDecimal sum = lines.stream().map(l -> (BigDecimal) l.get("total")).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo((BigDecimal) inv.get("total")) != 0) {
            issues.add(new Issue("TOTAL_MISMATCH", "ERROR", "invoice.total", "The invoice total " + inv.get("total") + " does not equal the sum of its lines " + sum + "."));
        }
        if (lines.stream().anyMatch(l -> ((BigDecimal) l.get("unitPrice")).signum() == 0)) {
            issues.add(new Issue("ZERO_PRICE_LINE", "WARNING", "lines", "At least one charge line has a zero price."));
        }
        OffsetDateTime started = (OffsetDateTime) enc.get("startedAt");
        OffsetDateTime ended = (OffsetDateTime) enc.get("endedAt");
        if (ended != null && ended.isBefore(started)) {
            issues.add(new Issue("ENCOUNTER_DATES", "ERROR", "encounter.endedAt", "The encounter ends before it starts."));
        }

        Map<String, Object> bundle = new TreeMap<>();
        bundle.put("schema", "hms-madai-internal/1");
        bundle.put("notFor", "Not a DHA eClaims FHIR resource. Internal snapshot for readiness checking only.");
        bundle.put("invoice", inv);
        bundle.put("facility", fac);
        bundle.put("patient", pat);
        bundle.put("encounter", enc);
        bundle.put("diagnoses", dx);
        bundle.put("lines", lines);
        String json = write(bundle);
        return new Built(bundle, json, sha256(json), (BigDecimal) inv.get("total"), issues);
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String statusOf(List<Issue> issues) {
        return count(issues, "ERROR") > 0 ? "NEEDS_ATTENTION" : "READY";
    }

    private static long count(List<Issue> issues, String severity) {
        return issues.stream().filter(i -> severity.equals(i.severity())).count();
    }

    private void storeIssues(TenantContext.Tenant t, UUID claimId, List<Issue> issues) {
        for (Issue i : issues) {
            jdbc.sql("INSERT INTO claim_issues (org_id, claim_id, rule_code, severity, field, message) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(t.orgId(), claimId, i.ruleCode(), i.severity(), i.field(), i.message()).update();
        }
    }

    private byte[] hashOf(UUID id) {
        return jdbc.sql("SELECT bundle_hash FROM claims WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query(byte[].class).single();
    }

    private String write(Object o) {
        try {
            return canonical.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String number(TenantContext.Tenant t, UUID facilityId) {
        jdbc.sql("INSERT INTO facility_counters (org_id, facility_id, name) VALUES (?, ?, 'CLM') ON CONFLICT DO NOTHING").params(t.orgId(), facilityId).update();
        long n = jdbc.sql("UPDATE facility_counters SET next_value = next_value + 1 WHERE facility_id = ? AND name = 'CLM' RETURNING next_value - 1").param(facilityId).query(Long.class).single();
        return String.format("CLM-%06d", n);
    }

    private Claim lock(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        jdbc.sql("SELECT id FROM claims WHERE org_id = ? AND id = ? FOR UPDATE").params(t.orgId(), id).query(UUID.class).optional().orElseThrow(() -> ApiException.notFound("Claim"));
        Claim c = load(id, false);
        t.requireFacility(c.facilityId());
        return c;
    }

    private Claim load(UUID id, boolean full) {
        TenantContext.Tenant t = TenantContext.require();
        Object[] row = jdbc.sql("""
                SELECT c.id, c.facility_id, c.patient_id, pt.given_name || ' ' || pt.family_name AS patient_name, c.encounter_id, c.invoice_id, c.claim_number, c.payer_type, c.status,
                       c.total, c.assembled_at, c.withdraw_reason, c.version, c.bundle::text AS bundle
                  FROM claims c JOIN patients pt ON pt.org_id = c.org_id AND pt.id = c.patient_id WHERE c.org_id = ? AND c.id = ?""").params(t.orgId(), id)
                .query((rs, n) -> new Object[] {new Claim(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                        rs.getObject("encounter_id", UUID.class), rs.getObject("invoice_id", UUID.class), rs.getString("claim_number"), rs.getString("payer_type"), rs.getString("status"),
                        rs.getBigDecimal("total"), rs.getObject("assembled_at", OffsetDateTime.class).toInstant(), rs.getString("withdraw_reason"), rs.getInt("version"), List.of(), null, List.of(),
                        DISCLAIMER), rs.getString("bundle")}).optional().orElseThrow(() -> ApiException.notFound("Claim"));
        Claim base = (Claim) row[0];
        if (!full) {
            return base;
        }
        List<Issue> issues = jdbc.sql("SELECT rule_code, severity, field, message FROM claim_issues WHERE org_id = ? AND claim_id = ? ORDER BY (severity = 'ERROR') DESC, rule_code, id")
                .params(t.orgId(), id).query((rs, n) -> new Issue(rs.getString("rule_code"), rs.getString("severity"), rs.getString("field"), rs.getString("message"))).list();
        List<Submission> subs = jdbc.sql("SELECT id, adapter, verified, sent, outcome, detail, submitted_at, submitted_by FROM claim_submissions WHERE org_id = ? AND claim_id = ? ORDER BY submitted_at DESC")
                .params(t.orgId(), id).query(ClaimsService::submission).list();
        Map<String, Object> bundle;
        try {
            bundle = canonical.readValue((String) row[1], new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new Claim(base.id(), base.facilityId(), base.patientId(), base.patientName(), base.encounterId(), base.invoiceId(), base.claimNumber(), base.payerType(), base.status(),
                base.total(), base.assembledAt(), base.withdrawReason(), base.version(), issues, bundle, subs, DISCLAIMER);
    }

    private static Submission submission(ResultSet rs, int n) throws SQLException {
        return new Submission(rs.getObject("id", UUID.class), rs.getString("adapter"), rs.getBoolean("verified"), rs.getBoolean("sent"), rs.getString("outcome"), rs.getString("detail"),
                rs.getObject("submitted_at", OffsetDateTime.class).toInstant(), rs.getObject("submitted_by", UUID.class));
    }
}
