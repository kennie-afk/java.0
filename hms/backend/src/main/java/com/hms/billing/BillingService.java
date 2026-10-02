package com.hms.billing;

import static com.hms.billing.BillingModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import com.hms.registry.Phones;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoices and payments. Money is BigDecimal in KES to two places and every rule that protects it
 * is also a database constraint: lines freeze when an invoice is issued, paid can never exceed the
 * total, a dispensing is billed once, a retried payment (same idempotency key) is not charged twice.
 */
@Service
public class BillingService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;
    private final MpesaGateway mpesa;

    public BillingService(JdbcClient jdbc, AuditService audit, PatientAccess patients, MpesaGateway mpesa) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
        this.mpesa = mpesa;
    }

    // ---- price list --------------------------------------------------------------------------

    @Transactional
    public Charge createCharge(ChargeInput in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID id;
        try {
            id = jdbc.sql("INSERT INTO charge_items (org_id, code, name, category, price, active) VALUES (?, ?, ?, ?, ?, ?) RETURNING id")
                    .params(t.orgId(), in.code(), in.name().trim(), in.category(), money(in.price()), in.active() == null || in.active()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("charge_exists", "A price list item with that code already exists.");
        }
        audit.record("charge.create", "charge_item", id, null, null, Map.of("code", in.code()));
        return charge(id);
    }

    @Transactional
    public Charge updateCharge(UUID id, ChargeInput in) {
        TenantContext.Tenant t = TenantContext.require();
        charge(id);
        try {
            jdbc.sql("UPDATE charge_items SET code = ?, name = ?, category = ?, price = ?, active = ? WHERE org_id = ? AND id = ?")
                    .params(in.code(), in.name().trim(), in.category(), money(in.price()), in.active() == null || in.active(), t.orgId(), id).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("charge_exists", "A price list item with that code already exists.");
        }
        audit.record("charge.update", "charge_item", id, null, null, Map.of("price", money(in.price()).toPlainString()));
        return charge(id);
    }

    @Transactional(readOnly = true)
    public List<Charge> charges(String q, boolean activeOnly) {
        List<Object> p = new ArrayList<>(List.of(TenantContext.require().orgId()));
        String sql = "SELECT id, code, name, category, price, active FROM charge_items WHERE org_id = ?" + (activeOnly ? " AND active" : "");
        if (q != null && !q.isBlank()) {
            sql += " AND (lower(name) LIKE ? OR code LIKE ?)";
            p.add("%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            p.add(q.trim().toUpperCase().replace("%", "") + "%");
        }
        return jdbc.sql(sql + " ORDER BY name LIMIT 500").params(p.toArray()).query(BillingService::chargeMap).list();
    }

    private Charge charge(UUID id) {
        return jdbc.sql("SELECT id, code, name, category, price, active FROM charge_items WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id)
                .query(BillingService::chargeMap).optional().orElseThrow(() -> ApiException.notFound("Price list item"));
    }

    private static Charge chargeMap(ResultSet rs, int n) throws SQLException {
        return new Charge(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("category"), rs.getBigDecimal("price"), rs.getBoolean("active"));
    }

    // ---- invoices ----------------------------------------------------------------------------

    @Transactional
    public Invoice create(InvoiceInput in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        patients.requireLive(in.patientId());
        if (in.encounterId() != null) {
            Long ok = jdbc.sql("SELECT count(*) FROM encounters WHERE org_id = ? AND id = ? AND patient_id = ? AND facility_id = ?")
                    .params(t.orgId(), in.encounterId(), in.patientId(), in.facilityId()).query(Long.class).single();
            if (ok == 0) {
                throw ApiException.badRequest("encounter_invalid", "That encounter does not belong to this patient at this facility.");
            }
        }
        String payerType = in.payerType() == null ? "CASH" : in.payerType();
        UUID id = jdbc.sql("""
                INSERT INTO invoices (org_id, facility_id, patient_id, encounter_id, invoice_number, payer_type, payer_name, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(t.orgId(), in.facilityId(), in.patientId(), in.encounterId(), number(t, in.facilityId(), "INV"), payerType, blank(in.payerName()), t.practitionerId())
                .query(UUID.class).single();
        audit.record("invoice.create", "invoice", id, in.facilityId(), null, Map.of("patient", in.patientId().toString()));
        return load(id);
    }

    @Transactional
    public Invoice addLine(UUID invoiceId, LineInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Invoice inv = lockDraft(invoiceId);
        String description;
        BigDecimal price;
        UUID sourceId = null;
        String sourceType = "MANUAL";
        if (in.chargeId() != null) {
            Charge c = charge(in.chargeId());
            if (!c.active()) {
                throw ApiException.conflict("charge_inactive", "That price list item is no longer active.");
            }
            description = c.name();
            price = c.price();
            sourceType = "CHARGE";
        } else {
            if (blank(in.description()) == null || in.unitPrice() == null) {
                throw ApiException.badRequest("line_incomplete", "Give a price list item, or a description and a unit price.");
            }
            description = in.description().trim();
            price = money(in.unitPrice());
        }
        jdbc.sql("INSERT INTO invoice_lines (org_id, invoice_id, description, source_type, source_id, quantity, unit_price) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(t.orgId(), invoiceId, description, sourceType, sourceId, in.quantity(), price).update();
        retotal(invoiceId);
        audit.record("invoice.line", "invoice", invoiceId, inv.facilityId(), null, Map.of("description", description));
        return load(invoiceId);
    }

    @Transactional
    public Invoice removeLine(UUID invoiceId, UUID lineId) {
        TenantContext.Tenant t = TenantContext.require();
        Invoice inv = lockDraft(invoiceId);
        var line = jdbc.sql("SELECT source_type, source_id FROM invoice_lines WHERE org_id = ? AND invoice_id = ? AND id = ?").params(t.orgId(), invoiceId, lineId)
                .query((rs, n) -> new Object[] {rs.getString("source_type"), rs.getObject("source_id", UUID.class)}).optional().orElseThrow(() -> ApiException.notFound("Line"));
        jdbc.sql("DELETE FROM invoice_lines WHERE org_id = ? AND id = ?").params(t.orgId(), lineId).update();
        if (line[1] != null) {
            jdbc.sql("DELETE FROM billed_sources WHERE org_id = ? AND source_type = ? AND source_id = ?").params(t.orgId(), line[0], line[1]).update();
        }
        retotal(invoiceId);
        audit.record("invoice.line.remove", "invoice", invoiceId, inv.facilityId(), null, Map.of());
        return load(invoiceId);
    }

    /**
     * Adds what the encounter actually consumed: every dispensing (drug price x quantity) and every laboratory
     * test that has a result, each at most once across all invoices. Safe to run again.
     */
    @Transactional
    public Invoice importEncounter(UUID invoiceId) {
        TenantContext.Tenant t = TenantContext.require();
        Invoice inv = lockDraft(invoiceId);
        if (inv.encounterId() == null) {
            throw ApiException.badRequest("no_encounter", "This invoice is not linked to an encounter.");
        }
        int added = 0;
        var dispensings = jdbc.sql("""
                SELECT d.id, dr.generic_name || coalesce(' ' || dr.strength, '') AS name, d.quantity, dr.unit_price
                  FROM dispensings d JOIN orders o ON o.org_id = d.org_id AND o.id = d.order_id JOIN drugs dr ON dr.org_id = d.org_id AND dr.id = d.drug_id
                 WHERE d.org_id = ? AND o.encounter_id = ? ORDER BY d.dispensed_at""").params(t.orgId(), inv.encounterId())
                .query((rs, n) -> new Object[] {rs.getObject("id", UUID.class), rs.getString("name"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price")}).list();
        for (Object[] d : dispensings) {
            added += claimSource(t, invoiceId, "DISPENSING", (UUID) d[0], "Dispensed: " + d[1], (BigDecimal) d[2], (BigDecimal) d[3]);
        }
        var labs = jdbc.sql("""
                SELECT i.id, lt.name, lt.price FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id JOIN lab_tests lt ON lt.org_id = i.org_id AND lt.id = i.test_id
                 WHERE i.org_id = ? AND o.encounter_id = ? AND i.status IN ('RESULTED', 'VALIDATED') ORDER BY o.created_at""").params(t.orgId(), inv.encounterId())
                .query((rs, n) -> new Object[] {rs.getObject("id", UUID.class), rs.getString("name"), rs.getBigDecimal("price")}).list();
        for (Object[] l : labs) {
            added += claimSource(t, invoiceId, "LAB", (UUID) l[0], "Laboratory: " + l[1], BigDecimal.ONE, (BigDecimal) l[2]);
        }
        retotal(invoiceId);
        audit.record("invoice.import", "invoice", invoiceId, inv.facilityId(), null, Map.of("added", added));
        return load(invoiceId);
    }

    private int claimSource(TenantContext.Tenant t, UUID invoiceId, String type, UUID sourceId, String description, BigDecimal qty, BigDecimal price) {
        int claimed = jdbc.sql("INSERT INTO billed_sources (org_id, source_type, source_id, invoice_id) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")
                .params(t.orgId(), type, sourceId, invoiceId).update();
        if (claimed == 0) {
            return 0;
        }
        jdbc.sql("INSERT INTO invoice_lines (org_id, invoice_id, description, source_type, source_id, quantity, unit_price) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(t.orgId(), invoiceId, description, type, sourceId, qty, price).update();
        return 1;
    }

    @Transactional
    public Invoice issue(UUID invoiceId) {
        TenantContext.Tenant t = TenantContext.require();
        Invoice inv = lockDraft(invoiceId);
        if (inv.lines().isEmpty() || inv.total().signum() <= 0) {
            throw ApiException.conflict("empty_invoice", "An invoice needs at least one priced line before it can be issued.");
        }
        jdbc.sql("UPDATE invoices SET status = 'ISSUED', issued_at = now(), version = version + 1 WHERE org_id = ? AND id = ?").params(t.orgId(), invoiceId).update();
        audit.record("invoice.issue", "invoice", invoiceId, inv.facilityId(), null, Map.of("total", inv.total().toPlainString()));
        return load(invoiceId);
    }

    @Transactional
    public Invoice voidInvoice(UUID invoiceId, VoidInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Invoice inv = lock(invoiceId);
        if ("VOID".equals(inv.status())) {
            throw ApiException.conflict("already_void", "That invoice is already void.");
        }
        Long taken = jdbc.sql("SELECT count(*) FROM payments WHERE org_id = ? AND invoice_id = ? AND status IN ('COMPLETED', 'PENDING')").params(t.orgId(), invoiceId).query(Long.class).single();
        if (taken > 0) {
            throw ApiException.conflict("has_payments", "Reverse the payments before voiding the invoice.");
        }
        jdbc.sql("UPDATE invoices SET status = 'VOID', void_reason = ?, version = version + 1 WHERE org_id = ? AND id = ?").params(in.reason().trim(), t.orgId(), invoiceId).update();
        jdbc.sql("DELETE FROM billed_sources WHERE org_id = ? AND invoice_id = ?").params(t.orgId(), invoiceId).update();
        audit.record("invoice.void", "invoice", invoiceId, inv.facilityId(), in.reason().trim(), Map.of());
        return load(invoiceId);
    }

    // ---- payments ----------------------------------------------------------------------------

    @Transactional
    public Payment pay(UUID invoiceId, PaymentInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Payment existing = byKey(in.idempotencyKey());
        if (existing != null) {
            if (!existing.invoiceId().equals(invoiceId) || existing.amount().compareTo(money(in.amount())) != 0) {
                throw ApiException.conflict("idempotency_conflict", "That idempotency key was already used for a different payment.");
            }
            return existing;
        }
        Invoice inv = lockPayable(invoiceId);
        BigDecimal amount = money(in.amount());
        requireWithinBalance(inv, amount);
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO payments (org_id, facility_id, invoice_id, method, amount, status, reference, idempotency_key, received_by, completed_at)
                    VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, now()) RETURNING id""")
                    .params(t.orgId(), inv.facilityId(), invoiceId, in.method(), amount, blank(in.reference()), in.idempotencyKey(), t.practitionerId()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("duplicate_payment", "That payment was just recorded by a parallel request.");
        }
        settle(inv, id);
        return payment(id);
    }

    @Transactional
    public StkResponse stkPush(UUID invoiceId, StkInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Payment existing = byKey(in.idempotencyKey());
        if (existing != null) {
            return new StkResponse(existing, "Already requested.");
        }
        Invoice inv = lockPayable(invoiceId);
        BigDecimal amount = money(in.amount());
        requireWithinBalance(inv, amount);
        String phone;
        try {
            phone = Phones.normalise(in.phone());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_phone", "That is not a Kenyan mobile number.");
        }
        MpesaGateway.StkRequest req = mpesa.initiate(phone, amount, inv.invoiceNumber(), "Hospital bill " + inv.invoiceNumber());
        UUID id = jdbc.sql("""
                INSERT INTO payments (org_id, facility_id, invoice_id, method, amount, status, idempotency_key, mpesa_phone, mpesa_checkout_id, received_by)
                VALUES (?, ?, ?, 'MPESA', ?, 'PENDING', ?, ?, ?, ?) RETURNING id""")
                .params(t.orgId(), inv.facilityId(), invoiceId, amount, in.idempotencyKey(), phone, req.checkoutRequestId(), t.practitionerId()).query(UUID.class).single();
        audit.record("payment.mpesa.request", "invoice", invoiceId, inv.facilityId(), null, Map.of("amount", amount.toPlainString(), "mock", mpesa.isMock()));
        return new StkResponse(payment(id), req.note());
    }

    /**
     * Simulates Safaricom's confirmation callback. Available only while the gateway is the mock: a
     * real callback needs its own authentication, which has not been designed against Daraja here.
     */
    @Transactional
    public Payment completeMock(MockCompletion in) {
        TenantContext.Tenant t = TenantContext.require();
        if (!mpesa.isMock()) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_IMPLEMENTED, "mpesa_not_configured", "Simulated completion is only available with the mock gateway.");
        }
        UUID id = jdbc.sql("SELECT id FROM payments WHERE org_id = ? AND mpesa_checkout_id = ?").params(t.orgId(), in.checkoutRequestId()).query(UUID.class).optional()
                .orElseThrow(() -> ApiException.notFound("M-Pesa request"));
        Payment p = payment(id);
        Invoice inv = lock(p.invoiceId());
        t.requireFacility(inv.facilityId());
        // Re-read under the lock: a duplicate callback must not complete twice.
        p = payment(id);
        if (!"PENDING".equals(p.status())) {
            return p;
        }
        if (Boolean.TRUE.equals(in.success())) {
            if ("VOID".equals(inv.status())) {
                throw ApiException.conflict("invoice_void", "That invoice was voided while the payment was pending.");
            }
            requireWithinBalance(inv, p.amount());
            String receipt = blank(in.receiptNumber()) == null ? "MOCK" + id.toString().substring(0, 8).toUpperCase() : in.receiptNumber().trim();
            try {
                jdbc.sql("UPDATE payments SET status = 'COMPLETED', completed_at = now(), mpesa_receipt = ? WHERE org_id = ? AND id = ?").params(receipt, t.orgId(), id).update();
            } catch (DuplicateKeyException e) {
                throw ApiException.conflict("receipt_reused", "That M-Pesa receipt number was already used.");
            }
            settle(inv, id);
        } else {
            jdbc.sql("UPDATE payments SET status = 'FAILED', failure_reason = ?, completed_at = now() WHERE org_id = ? AND id = ?")
                    .params(blank(in.failureReason()) == null ? "Not completed by the customer" : in.failureReason().trim(), t.orgId(), id).update();
            audit.record("payment.mpesa.failed", "invoice", inv.id(), inv.facilityId(), null, Map.of("payment", id.toString()));
        }
        return payment(id);
    }

    @Transactional
    public Payment reverse(UUID paymentId, ReverseInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Payment p = payment(paymentId);
        Invoice inv = lock(p.invoiceId());
        t.requireFacility(inv.facilityId());
        p = payment(paymentId);
        if (!"COMPLETED".equals(p.status())) {
            throw ApiException.conflict("not_reversible", "Only a completed payment can be reversed.");
        }
        jdbc.sql("UPDATE payments SET status = 'REVERSED', reversed_by = ?, reversed_at = now(), reversal_reason = ? WHERE org_id = ? AND id = ?")
                .params(t.practitionerId(), in.reason().trim(), t.orgId(), paymentId).update();
        refreshPaid(inv.id());
        audit.record("payment.reverse", "invoice", inv.id(), inv.facilityId(), in.reason().trim(), Map.of("payment", paymentId.toString(), "amount", p.amount().toPlainString()));
        return payment(paymentId);
    }

    private void settle(Invoice inv, UUID paymentId) {
        TenantContext.Tenant t = TenantContext.require();
        String rn = number(t, inv.facilityId(), "RCT");
        jdbc.sql("INSERT INTO receipts (org_id, facility_id, payment_id, receipt_number) VALUES (?, ?, ?, ?)").params(t.orgId(), inv.facilityId(), paymentId, rn).update();
        refreshPaid(inv.id());
        audit.record("payment.complete", "invoice", inv.id(), inv.facilityId(), null, Map.of("payment", paymentId.toString(), "receipt", rn));
    }

    /** Recomputes paid and status from the payments themselves: the stored figure can never drift from them. */
    private void refreshPaid(UUID invoiceId) {
        TenantContext.Tenant t = TenantContext.require();
        jdbc.sql("""
                UPDATE invoices i SET amount_paid = s.paid, version = version + 1,
                       status = CASE WHEN s.paid >= i.total THEN 'PAID' WHEN s.paid > 0 THEN 'PARTIALLY_PAID' ELSE 'ISSUED' END
                  FROM (SELECT coalesce(sum(amount), 0) AS paid FROM payments WHERE org_id = ? AND invoice_id = ? AND status = 'COMPLETED') s
                 WHERE i.org_id = ? AND i.id = ?""").params(t.orgId(), invoiceId, t.orgId(), invoiceId).update();
    }

    /** A payment can never exceed what is still owed. A pending M-Pesa request is checked again when it completes. */
    private void requireWithinBalance(Invoice inv, BigDecimal amount) {
        BigDecimal balance = inv.total().subtract(inv.amountPaid());
        if (amount.compareTo(balance) > 0) {
            throw ApiException.conflict("overpayment", "That is more than the balance of KES " + balance.toPlainString() + ".");
        }
    }

    // ---- reading -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Invoice open(UUID id) {
        Invoice inv = load(id);
        TenantContext.require().requireFacility(inv.facilityId());
        patients.require(inv.patientId());
        return inv;
    }

    @Transactional(readOnly = true)
    public Slice<InvoiceRow> list(UUID facilityId, UUID patientId, String status, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(t.orgId()));
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.invoice_number, i.patient_id, pt.given_name || ' ' || pt.family_name AS patient_name, i.status, i.payer_type, i.total, i.amount_paid, i.created_at
                  FROM invoices i JOIN patients pt ON pt.org_id = i.org_id AND pt.id = i.patient_id WHERE i.org_id = ?""");
        if (facilityId != null) {
            t.requireFacility(facilityId);
            sql.append(" AND i.facility_id = ?");
            p.add(facilityId);
        } else {
            sql.append(" AND i.facility_id = ANY (?)");
            p.add(t.facilityIds().toArray(UUID[]::new));
        }
        if (patientId != null) {
            patients.require(patientId);
            sql.append(" AND i.patient_id = ?");
            p.add(patientId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND i.status = ?");
            p.add(status);
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (i.created_at, i.id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY i.created_at DESC, i.id DESC LIMIT ?");
        p.add(size + 1);
        List<InvoiceRow> rows = jdbc.sql(sql.toString()).params(p.toArray())
                .query((rs, n) -> new InvoiceRow(rs.getObject("id", UUID.class), rs.getString("invoice_number"), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                        rs.getString("status"), rs.getString("payer_type"), rs.getBigDecimal("total"), rs.getBigDecimal("amount_paid"), rs.getObject("created_at", OffsetDateTime.class).toInstant())).list();
        boolean more = rows.size() > size;
        List<InvoiceRow> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).createdAt().toString(), "i", page.get(page.size() - 1).id().toString())) : null;
        return new Slice<>(page, next);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Invoice lock(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        jdbc.sql("SELECT id FROM invoices WHERE org_id = ? AND id = ? FOR UPDATE").params(t.orgId(), id).query(UUID.class).optional().orElseThrow(() -> ApiException.notFound("Invoice"));
        Invoice inv = load(id);
        t.requireFacility(inv.facilityId());
        return inv;
    }

    private Invoice lockDraft(UUID id) {
        Invoice inv = lock(id);
        if (!"DRAFT".equals(inv.status())) {
            throw ApiException.conflict("invoice_frozen", "Only a draft invoice can be changed (this one is " + inv.status() + ").");
        }
        return inv;
    }

    private Invoice lockPayable(UUID id) {
        Invoice inv = lock(id);
        if (!List.of("ISSUED", "PARTIALLY_PAID").contains(inv.status())) {
            throw ApiException.conflict("not_payable", "An invoice can be paid once issued and until it is paid in full (this one is " + inv.status() + ").");
        }
        return inv;
    }

    private void retotal(UUID invoiceId) {
        TenantContext.Tenant t = TenantContext.require();
        jdbc.sql("UPDATE invoices SET total = (SELECT coalesce(sum(line_total), 0) FROM invoice_lines WHERE org_id = ? AND invoice_id = ?), version = version + 1 WHERE org_id = ? AND id = ?")
                .params(t.orgId(), invoiceId, t.orgId(), invoiceId).update();
    }

    private String number(TenantContext.Tenant t, UUID facilityId, String prefix) {
        jdbc.sql("INSERT INTO facility_counters (org_id, facility_id, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").params(t.orgId(), facilityId, prefix).update();
        long n = jdbc.sql("UPDATE facility_counters SET next_value = next_value + 1 WHERE facility_id = ? AND name = ? RETURNING next_value - 1").params(facilityId, prefix).query(Long.class).single();
        return String.format("%s-%06d", prefix, n);
    }

    private Payment byKey(String key) {
        return jdbc.sql(PAY_SQL + " WHERE p.org_id = ? AND p.idempotency_key = ?").params(TenantContext.require().orgId(), key).query(BillingService::paymentMap).optional().orElse(null);
    }

    private Payment payment(UUID id) {
        return jdbc.sql(PAY_SQL + " WHERE p.org_id = ? AND p.id = ?").params(TenantContext.require().orgId(), id).query(BillingService::paymentMap).optional()
                .orElseThrow(() -> ApiException.notFound("Payment"));
    }

    private static final String PAY_SQL = """
            SELECT p.id, p.invoice_id, p.method, p.amount, p.status, p.reference, p.mpesa_phone, p.mpesa_checkout_id, p.mpesa_receipt, p.failure_reason, r.receipt_number,
                   p.created_at, p.completed_at, p.reversal_reason
              FROM payments p LEFT JOIN receipts r ON r.org_id = p.org_id AND r.payment_id = p.id""";

    private static Payment paymentMap(ResultSet rs, int n) throws SQLException {
        OffsetDateTime done = rs.getObject("completed_at", OffsetDateTime.class);
        return new Payment(rs.getObject("id", UUID.class), rs.getObject("invoice_id", UUID.class), rs.getString("method"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getString("reference"), rs.getString("mpesa_phone"), rs.getString("mpesa_checkout_id"), rs.getString("mpesa_receipt"), rs.getString("failure_reason"),
                rs.getString("receipt_number"), rs.getObject("created_at", OffsetDateTime.class).toInstant(), done == null ? null : done.toInstant(), rs.getString("reversal_reason"));
    }

    private Invoice load(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        Invoice base = jdbc.sql("""
                SELECT i.id, i.facility_id, i.patient_id, pt.given_name || ' ' || pt.family_name AS patient_name, i.encounter_id, i.invoice_number, i.status, i.payer_type, i.payer_name,
                       i.total, i.amount_paid, i.currency, i.issued_at, i.void_reason, i.version, i.created_at
                  FROM invoices i JOIN patients pt ON pt.org_id = i.org_id AND pt.id = i.patient_id WHERE i.org_id = ? AND i.id = ?""").params(t.orgId(), id)
                .query((rs, n) -> {
                    OffsetDateTime issued = rs.getObject("issued_at", OffsetDateTime.class);
                    return new Invoice(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                            rs.getObject("encounter_id", UUID.class), rs.getString("invoice_number"), rs.getString("status"), rs.getString("payer_type"), rs.getString("payer_name"),
                            rs.getBigDecimal("total"), rs.getBigDecimal("amount_paid"), rs.getBigDecimal("total").subtract(rs.getBigDecimal("amount_paid")), rs.getString("currency"),
                            issued == null ? null : issued.toInstant(), rs.getString("void_reason"), rs.getInt("version"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                            List.of(), List.of());
                }).optional().orElseThrow(() -> ApiException.notFound("Invoice"));
        List<Line> lines = jdbc.sql("SELECT id, description, source_type, source_id, quantity, unit_price, line_total FROM invoice_lines WHERE org_id = ? AND invoice_id = ? ORDER BY description, id")
                .params(t.orgId(), id).query((rs, n) -> new Line(rs.getObject("id", UUID.class), rs.getString("description"), rs.getString("source_type"), rs.getObject("source_id", UUID.class),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total"))).list();
        List<Payment> payments = jdbc.sql(PAY_SQL + " WHERE p.org_id = ? AND p.invoice_id = ? ORDER BY p.created_at").params(t.orgId(), id).query(BillingService::paymentMap).list();
        return new Invoice(base.id(), base.facilityId(), base.patientId(), base.patientName(), base.encounterId(), base.invoiceNumber(), base.status(), base.payerType(), base.payerName(),
                base.total(), base.amountPaid(), base.balance(), base.currency(), base.issuedAt(), base.voidReason(), base.version(), base.createdAt(), lines, payments);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
