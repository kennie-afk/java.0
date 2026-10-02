package com.hms.lab;

import static com.hms.lab.LabModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Laboratory. A result is entered by one person and validated by another; clinicians see only
 * validated results; a critical value stays flagged until a named person acknowledges it; a change
 * to a validated result is an amendment with a reason, kept in the result history.
 */
@Service
public class LabService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;

    public LabService(JdbcClient jdbc, AuditService audit, PatientAccess patients) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
    }

    // ---- catalogue ---------------------------------------------------------------------------

    @Transactional
    public Test createTest(TestInput in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO lab_tests (org_id, code, name, loinc_code, specimen_type, result_type, unit, ref_low, ref_high, critical_low, critical_high, price, active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), in.code(), in.name().trim(), blank(in.loincCode()), in.specimenType() == null ? "BLOOD" : in.specimenType(),
                            in.resultType() == null ? "NUMERIC" : in.resultType(), blank(in.unit()), in.refLow(), in.refHigh(), in.criticalLow(), in.criticalHigh(),
                            in.price() == null ? BigDecimal.ZERO : in.price(), in.active() == null || in.active()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("test_exists", "A test with that code already exists.");
        } catch (DataIntegrityViolationException e) {
            throw ApiException.badRequest("ranges_invalid", "Reference and critical ranges must be in order: critical low <= low <= high <= critical high.");
        }
        audit.record("lab.test.create", "lab_test", id, null, null, Map.of("code", in.code()));
        return test(id);
    }

    @Transactional
    public Test updateTest(UUID id, TestInput in) {
        TenantContext.Tenant t = TenantContext.require();
        test(id);
        try {
            jdbc.sql("""
                    UPDATE lab_tests SET code = ?, name = ?, loinc_code = ?, specimen_type = ?, result_type = ?, unit = ?, ref_low = ?, ref_high = ?,
                           critical_low = ?, critical_high = ?, price = ?, active = ? WHERE org_id = ? AND id = ?""")
                    .params(in.code(), in.name().trim(), blank(in.loincCode()), in.specimenType() == null ? "BLOOD" : in.specimenType(),
                            in.resultType() == null ? "NUMERIC" : in.resultType(), blank(in.unit()), in.refLow(), in.refHigh(), in.criticalLow(), in.criticalHigh(),
                            in.price() == null ? BigDecimal.ZERO : in.price(), in.active() == null || in.active(), t.orgId(), id).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("test_exists", "A test with that code already exists.");
        } catch (DataIntegrityViolationException e) {
            throw ApiException.badRequest("ranges_invalid", "Reference and critical ranges must be in order: critical low <= low <= high <= critical high.");
        }
        audit.record("lab.test.update", "lab_test", id, null, null, Map.of());
        return test(id);
    }

    @Transactional(readOnly = true)
    public List<Test> tests(String q, boolean activeOnly) {
        List<Object> p = new ArrayList<>(List.of(TenantContext.require().orgId()));
        String sql = TEST_SQL + " WHERE org_id = ?" + (activeOnly ? " AND active" : "");
        if (q != null && !q.isBlank()) {
            sql += " AND (lower(name) LIKE ? OR code LIKE ?)";
            p.add("%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            p.add(q.trim().toUpperCase().replace("%", "") + "%");
        }
        return jdbc.sql(sql + " ORDER BY name LIMIT 500").params(p.toArray()).query(LabService::testMap).list();
    }

    private static final String TEST_SQL = "SELECT id, code, name, loinc_code, specimen_type, result_type, unit, ref_low, ref_high, critical_low, critical_high, price, active FROM lab_tests";

    private Test test(UUID id) {
        return jdbc.sql(TEST_SQL + " WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query(LabService::testMap).optional()
                .orElseThrow(() -> ApiException.notFound("Test"));
    }

    private static Test testMap(ResultSet rs, int n) throws SQLException {
        return new Test(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("loinc_code"), rs.getString("specimen_type"),
                rs.getString("result_type"), rs.getString("unit"), rs.getBigDecimal("ref_low"), rs.getBigDecimal("ref_high"), rs.getBigDecimal("critical_low"),
                rs.getBigDecimal("critical_high"), rs.getBigDecimal("price"), rs.getBoolean("active"));
    }

    // ---- orders ------------------------------------------------------------------------------

    @Transactional
    public Order order(OrderInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        patients.requireLive(in.patientId());
        if (in.encounterId() != null) {
            Long ok = jdbc.sql("SELECT count(*) FROM encounters WHERE org_id = ? AND id = ? AND patient_id = ? AND facility_id = ? AND status = 'OPEN'")
                    .params(t.orgId(), in.encounterId(), in.patientId(), in.facilityId()).query(Long.class).single();
            if (ok == 0) {
                throw ApiException.badRequest("encounter_invalid", "That is not an open encounter for this patient at this facility.");
            }
        }
        List<UUID> tests = new ArrayList<>(new LinkedHashSet<>(in.testIds()));
        for (UUID testId : tests) {
            if (!test(testId).active()) {
                throw ApiException.conflict("test_inactive", "That test is no longer offered.");
            }
        }
        String number = nextNumber(t, in.facilityId());
        UUID id = jdbc.sql("""
                INSERT INTO lab_orders (org_id, facility_id, patient_id, encounter_id, order_number, priority, clinical_info, ordered_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(t.orgId(), in.facilityId(), in.patientId(), in.encounterId(), number, in.priority() == null ? "ROUTINE" : in.priority(), blank(in.clinicalInfo()),
                        t.practitionerId()).query(UUID.class).single();
        for (UUID testId : tests) {
            jdbc.sql("INSERT INTO lab_order_items (org_id, order_id, test_id) VALUES (?, ?, ?)").params(t.orgId(), id, testId).update();
        }
        audit.record("lab.order", "patient", in.patientId(), in.facilityId(), null, Map.of("order", number, "tests", tests.size()));
        return load(id);
    }

    @Transactional
    public Order cancel(UUID orderId, CancelInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Order o = load(orderId);
        t.requireFacility(o.facilityId());
        if (o.items().stream().anyMatch(i -> List.of("RESULTED", "VALIDATED").contains(i.status()))) {
            throw ApiException.conflict("has_results", "An order with results cannot be cancelled.");
        }
        if ("CANCELLED".equals(o.status())) {
            throw ApiException.conflict("already_cancelled", "That order is already cancelled.");
        }
        jdbc.sql("UPDATE lab_order_items SET status = 'CANCELLED' WHERE org_id = ? AND order_id = ?").params(t.orgId(), orderId).update();
        jdbc.sql("UPDATE lab_orders SET status = 'CANCELLED', cancel_reason = ? WHERE org_id = ? AND id = ?").params(in.reason().trim(), t.orgId(), orderId).update();
        audit.record("lab.cancel", "patient", o.patientId(), o.facilityId(), in.reason().trim(), Map.of("order", o.orderNumber()));
        return load(orderId);
    }

    @Transactional
    public Order collect(UUID orderId, CollectInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Order o = load(orderId);
        t.requireFacility(o.facilityId());
        List<Item> targets = o.items().stream().filter(i -> "PENDING".equals(i.status()))
                .filter(i -> in == null || in.itemIds() == null || in.itemIds().contains(i.id())).toList();
        if (targets.isEmpty()) {
            throw ApiException.conflict("nothing_to_collect", "No tests on this order are waiting for a specimen.");
        }
        for (Item i : targets) {
            // The barcode is what travels on the tube and back to the result.
            String barcode = o.orderNumber() + "-" + i.testCode();
            jdbc.sql("UPDATE lab_order_items SET status = 'COLLECTED', specimen_barcode = ?, collected_at = now(), collected_by = ? WHERE org_id = ? AND id = ?")
                    .params(barcode, t.practitionerId(), t.orgId(), i.id()).update();
        }
        refreshOrderStatus(orderId);
        audit.record("lab.collect", "patient", o.patientId(), o.facilityId(), null, Map.of("order", o.orderNumber(), "specimens", targets.size()));
        return load(orderId);
    }

    @Transactional
    public Item enterResult(UUID itemId, ResultInput in) {
        TenantContext.Tenant t = TenantContext.require();
        ItemCtx c = lock(itemId);
        t.requireFacility(c.facilityId);
        if (!"COLLECTED".equals(c.status)) {
            throw ApiException.conflict("not_collected", "A result can be entered once, after the specimen is collected (this item is " + c.status + ").");
        }
        record(t, c, in.numeric(), in.text(), null);
        return item(itemId, true);
    }

    /** Changes a validated result. It goes back for a fresh validation by someone other than the amender. */
    @Transactional
    public Item amend(UUID itemId, AmendInput in) {
        TenantContext.Tenant t = TenantContext.require();
        ItemCtx c = lock(itemId);
        t.requireFacility(c.facilityId);
        if (!"VALIDATED".equals(c.status)) {
            throw ApiException.conflict("not_validated", "Only a validated result is amended; an unvalidated one can be re-entered by cancelling the order and reordering.");
        }
        jdbc.sql("UPDATE lab_order_items SET validated_by = NULL, validated_at = NULL WHERE org_id = ? AND id = ?").params(t.orgId(), itemId).update();
        record(t, c, in.numeric(), in.text(), in.reason().trim());
        audit.record("lab.amend", "patient", c.patientId, c.facilityId, in.reason().trim(), Map.of("item", itemId.toString()));
        return item(itemId, true);
    }

    private void record(TenantContext.Tenant t, ItemCtx c, BigDecimal numeric, String text, String reason) {
        if (c.resultType.equals("NUMERIC")) {
            if (numeric == null) {
                throw ApiException.badRequest("numeric_required", "This test reports a number.");
            }
            text = null;
        } else {
            if (text == null || text.isBlank()) {
                throw ApiException.badRequest("text_required", "This test reports text.");
            }
            numeric = null;
            text = text.trim();
        }
        String flag = flag(c, numeric);
        boolean critical = "LL".equals(flag) || "HH".equals(flag);
        int version = c.version + 1;
        jdbc.sql("""
                UPDATE lab_order_items SET status = 'RESULTED', result_numeric = ?, result_text = ?, flag = ?, critical = ?, entered_by = ?, entered_at = now(),
                       critical_ack_by = NULL, critical_ack_at = NULL, critical_ack_note = NULL, version = ? WHERE org_id = ? AND id = ?""")
                .params(numeric, text, flag, critical, t.practitionerId(), version, t.orgId(), c.id).update();
        jdbc.sql("INSERT INTO lab_result_history (org_id, item_id, version, result_numeric, result_text, flag, entered_by, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(t.orgId(), c.id, version, numeric, text, flag, t.practitionerId(), reason).update();
        refreshOrderStatus(c.orderId);
        audit.record("lab.result", "patient", c.patientId, c.facilityId, null, Map.of("item", c.id.toString(), "flag", flag == null ? "-" : flag, "critical", critical));
    }

    @Transactional
    public Item validate(UUID itemId) {
        TenantContext.Tenant t = TenantContext.require();
        ItemCtx c = lock(itemId);
        t.requireFacility(c.facilityId);
        if (!"RESULTED".equals(c.status)) {
            throw ApiException.conflict("not_resulted", "Only an entered result awaiting validation can be validated.");
        }
        if (t.practitionerId().equals(c.enteredBy)) {
            throw ApiException.forbidden("A result must be validated by someone other than the person who entered it.");
        }
        jdbc.sql("UPDATE lab_order_items SET status = 'VALIDATED', validated_by = ?, validated_at = now() WHERE org_id = ? AND id = ?").params(t.practitionerId(), t.orgId(), itemId).update();
        refreshOrderStatus(c.orderId);
        audit.record("lab.validate", "patient", c.patientId, c.facilityId, null, Map.of("item", itemId.toString()));
        return item(itemId, true);
    }

    @Transactional
    public Item acknowledge(UUID itemId, AckInput in) {
        TenantContext.Tenant t = TenantContext.require();
        ItemCtx c = lock(itemId);
        t.requireFacility(c.facilityId);
        if (!c.critical) {
            throw ApiException.conflict("not_critical", "That result is not a critical value.");
        }
        if (c.ackAt != null) {
            throw ApiException.conflict("already_acknowledged", "That critical value has already been acknowledged.");
        }
        jdbc.sql("UPDATE lab_order_items SET critical_ack_by = ?, critical_ack_at = now(), critical_ack_note = ? WHERE org_id = ? AND id = ?")
                .params(t.practitionerId(), in.note().trim(), t.orgId(), itemId).update();
        audit.record("lab.critical.ack", "patient", c.patientId, c.facilityId, in.note().trim(), Map.of("item", itemId.toString()));
        return item(itemId, true);
    }

    /** Critical values nobody has acknowledged, most recent first. */
    @Transactional(readOnly = true)
    public List<CriticalRow> critical(UUID facilityId) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        boolean sees = t.can(Permissions.LAB_ENTER) || t.can(Permissions.LAB_VALIDATE);
        return jdbc.sql("""
                SELECT i.id, o.id AS order_id, o.order_number, o.patient_id, p.given_name || ' ' || p.family_name AS patient_name, lt.name, i.result_numeric, lt.unit, i.flag, i.entered_at
                  FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id
                  JOIN lab_tests lt ON lt.org_id = i.org_id AND lt.id = i.test_id JOIN patients p ON p.org_id = o.org_id AND p.id = o.patient_id
                 WHERE i.org_id = ? AND o.facility_id = ? AND i.critical AND i.critical_ack_at IS NULL AND i.status IN ('RESULTED', 'VALIDATED') AND (? OR i.status = 'VALIDATED')
                 ORDER BY i.entered_at DESC LIMIT 200""").params(t.orgId(), facilityId, sees)
                .query((rs, n) -> new CriticalRow(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), rs.getString("order_number"),
                        rs.getObject("patient_id", UUID.class), rs.getString("patient_name"), rs.getString("name"), rs.getBigDecimal("result_numeric"), rs.getString("unit"),
                        rs.getString("flag"), rs.getObject("entered_at", OffsetDateTime.class).toInstant())).list();
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID itemId) {
        TenantContext.Tenant t = TenantContext.require();
        UUID facility = jdbc.sql("SELECT o.facility_id FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id WHERE i.org_id = ? AND i.id = ?")
                .params(t.orgId(), itemId).query(UUID.class).optional().orElseThrow(() -> ApiException.notFound("Result"));
        t.requireFacility(facility);
        return jdbc.sql("SELECT version, result_numeric, result_text, flag, entered_by, entered_at, reason FROM lab_result_history WHERE org_id = ? AND item_id = ? ORDER BY version")
                .params(t.orgId(), itemId).query((rs, n) -> new HistoryEntry(rs.getInt("version"), rs.getBigDecimal("result_numeric"), rs.getString("result_text"), rs.getString("flag"),
                        rs.getObject("entered_by", UUID.class), rs.getObject("entered_at", OffsetDateTime.class).toInstant(), rs.getString("reason"))).list();
    }

    @Transactional(readOnly = true)
    public Slice<OrderRow> list(UUID facilityId, UUID patientId, String status, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(t.orgId()));
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.order_number, o.patient_id, p.given_name || ' ' || p.family_name AS patient_name, o.priority, o.status, o.created_at,
                       (SELECT count(*) FROM lab_order_items i WHERE i.org_id = o.org_id AND i.order_id = o.id) AS items,
                       EXISTS (SELECT 1 FROM lab_order_items i WHERE i.org_id = o.org_id AND i.order_id = o.id AND i.critical AND i.critical_ack_at IS NULL) AS crit
                  FROM lab_orders o JOIN patients p ON p.org_id = o.org_id AND p.id = o.patient_id WHERE o.org_id = ?""");
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND o.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND o.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND o.patient_id = ?");
            p.add(patientId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND o.status = ?");
            p.add(status);
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (o.created_at, o.id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY o.created_at DESC, o.id DESC LIMIT ?");
        p.add(size + 1);
        List<OrderRow> rows = jdbc.sql(sql.toString()).params(p.toArray())
                .query((rs, n) -> new OrderRow(rs.getObject("id", UUID.class), rs.getString("order_number"), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                        rs.getString("priority"), rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getInt("items"), rs.getBoolean("crit"))).list();
        boolean more = rows.size() > size;
        List<OrderRow> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).createdAt().toString(), "i", page.get(page.size() - 1).id().toString())) : null;
        return new Slice<>(page, next);
    }

    @Transactional(readOnly = true)
    public Order open(UUID id) {
        Order o = load(id);
        TenantContext.require().requireFacility(o.facilityId());
        patients.require(o.patientId());
        return o;
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record ItemCtx(UUID id, UUID orderId, UUID facilityId, UUID patientId, String status, String resultType, int version, UUID enteredBy, boolean critical,
                           Instant ackAt, BigDecimal refLow, BigDecimal refHigh, BigDecimal critLow, BigDecimal critHigh) {}

    private ItemCtx lock(UUID itemId) {
        return jdbc.sql("""
                SELECT i.id, i.order_id, o.facility_id, o.patient_id, i.status, lt.result_type, i.version, i.entered_by, i.critical, i.critical_ack_at,
                       lt.ref_low, lt.ref_high, lt.critical_low, lt.critical_high
                  FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id JOIN lab_tests lt ON lt.org_id = i.org_id AND lt.id = i.test_id
                 WHERE i.org_id = ? AND i.id = ? FOR UPDATE OF i""").params(TenantContext.require().orgId(), itemId)
                .query((rs, n) -> new ItemCtx(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), rs.getObject("facility_id", UUID.class),
                        rs.getObject("patient_id", UUID.class), rs.getString("status"), rs.getString("result_type"), rs.getInt("version"), rs.getObject("entered_by", UUID.class),
                        rs.getBoolean("critical"), rs.getObject("critical_ack_at", OffsetDateTime.class) == null ? null : rs.getObject("critical_ack_at", OffsetDateTime.class).toInstant(),
                        rs.getBigDecimal("ref_low"), rs.getBigDecimal("ref_high"), rs.getBigDecimal("critical_low"), rs.getBigDecimal("critical_high")))
                .optional().orElseThrow(() -> ApiException.notFound("Result"));
    }

    /** L/H against the reference range, LL/HH against the critical limits. The ranges are the organisation's, one set for all ages and sexes. */
    private static String flag(ItemCtx c, BigDecimal v) {
        if (v == null) {
            return null;
        }
        if (c.critLow != null && v.compareTo(c.critLow) < 0) {
            return "LL";
        }
        if (c.critHigh != null && v.compareTo(c.critHigh) > 0) {
            return "HH";
        }
        if (c.refLow != null && v.compareTo(c.refLow) < 0) {
            return "L";
        }
        if (c.refHigh != null && v.compareTo(c.refHigh) > 0) {
            return "H";
        }
        return "N";
    }

    private void refreshOrderStatus(UUID orderId) {
        TenantContext.Tenant t = TenantContext.require();
        List<String> statuses = jdbc.sql("SELECT status FROM lab_order_items WHERE org_id = ? AND order_id = ? AND status <> 'CANCELLED'").params(t.orgId(), orderId).query(String.class).list();
        String status = "ORDERED";
        if (!statuses.isEmpty()) {
            if (statuses.stream().allMatch("VALIDATED"::equals)) {
                status = "VALIDATED";
            } else if (statuses.stream().allMatch(s -> s.equals("VALIDATED") || s.equals("RESULTED"))) {
                status = "RESULTED";
            } else if (statuses.stream().anyMatch(s -> !s.equals("PENDING"))) {
                status = "COLLECTED";
            }
        }
        jdbc.sql("UPDATE lab_orders SET status = ? WHERE org_id = ? AND id = ?").params(status, t.orgId(), orderId).update();
    }

    private String nextNumber(TenantContext.Tenant t, UUID facilityId) {
        jdbc.sql("INSERT INTO facility_counters (org_id, facility_id, name) VALUES (?, ?, 'LAB') ON CONFLICT DO NOTHING").params(t.orgId(), facilityId).update();
        long n = jdbc.sql("UPDATE facility_counters SET next_value = next_value + 1 WHERE facility_id = ? AND name = 'LAB' RETURNING next_value - 1").param(facilityId).query(Long.class).single();
        return String.format("LAB-%06d", n);
    }

    private Order load(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Order base = jdbc.sql("""
                SELECT o.id, o.facility_id, o.patient_id, p.given_name || ' ' || p.family_name AS patient_name, o.encounter_id, o.order_number, o.priority, o.status,
                       o.clinical_info, o.ordered_by, o.created_at
                  FROM lab_orders o JOIN patients p ON p.org_id = o.org_id AND p.id = o.patient_id WHERE o.org_id = ? AND o.id = ?""").params(t.orgId(), id)
                .query((rs, n) -> new Order(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                        rs.getObject("encounter_id", UUID.class), rs.getString("order_number"), rs.getString("priority"), rs.getString("status"), rs.getString("clinical_info"),
                        rs.getObject("ordered_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant(), List.of()))
                .optional().orElseThrow(() -> ApiException.notFound("Lab order"));
        boolean sees = t.can(Permissions.LAB_ENTER) || t.can(Permissions.LAB_VALIDATE);
        List<Item> items = jdbc.sql(ITEM_SQL + " WHERE i.org_id = ? AND i.order_id = ? ORDER BY lt.name").params(t.orgId(), id).query((rs, n) -> itemMap(rs, sees)).list();
        return new Order(base.id(), base.facilityId(), base.patientId(), base.patientName(), base.encounterId(), base.orderNumber(), base.priority(), base.status(), base.clinicalInfo(),
                base.orderedBy(), base.createdAt(), items);
    }

    private Item item(UUID id, boolean seesUnvalidated) {
        return jdbc.sql(ITEM_SQL + " WHERE i.org_id = ? AND i.id = ?").params(TenantContext.require().orgId(), id).query((rs, n) -> itemMap(rs, seesUnvalidated)).single();
    }

    private static final String ITEM_SQL = """
            SELECT i.id, i.test_id, lt.code, lt.name, lt.result_type, lt.unit, lt.ref_low, lt.ref_high, i.status, i.specimen_barcode, i.collected_at, i.result_numeric, i.result_text, i.flag,
                   i.critical, i.entered_by, i.entered_at, i.validated_by, i.validated_at, i.critical_ack_at, i.critical_ack_note, i.version
              FROM lab_order_items i JOIN lab_tests lt ON lt.org_id = i.org_id AND lt.id = i.test_id""";

    /** Clinicians (no enter/validate right) never see a result that has not been validated: it may still be wrong. */
    private static Item itemMap(ResultSet rs, boolean seesUnvalidated) throws SQLException {
        String status = rs.getString("status");
        boolean hide = !seesUnvalidated && "RESULTED".equals(status);
        return new Item(rs.getObject("id", UUID.class), rs.getObject("test_id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("result_type"), rs.getString("unit"),
                rs.getBigDecimal("ref_low"), rs.getBigDecimal("ref_high"), status, rs.getString("specimen_barcode"), instant(rs, "collected_at"),
                hide ? null : rs.getBigDecimal("result_numeric"), hide ? null : rs.getString("result_text"), hide ? null : rs.getString("flag"), !hide && rs.getBoolean("critical"),
                rs.getObject("entered_by", UUID.class), instant(rs, "entered_at"), rs.getObject("validated_by", UUID.class), instant(rs, "validated_at"), instant(rs, "critical_ack_at"),
                rs.getString("critical_ack_note"), rs.getInt("version"), hide);
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
