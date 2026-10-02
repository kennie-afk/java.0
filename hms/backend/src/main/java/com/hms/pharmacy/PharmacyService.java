package com.hms.pharmacy;

import static com.hms.pharmacy.PharmacyModels.*;

import com.hms.clinical.ClinicalService;
import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Formulary, stock and dispensing. Stock is only ever moved inside a transaction that locks the
 * batch rows first-expiry-first-out, so concurrent dispensing cannot oversell; expired stock is
 * never dispensed; a controlled drug needs a second, different, named person.
 */
@Service
public class PharmacyService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;
    private final ClinicalService clinical;

    public PharmacyService(JdbcClient jdbc, AuditService audit, PatientAccess patients, ClinicalService clinical) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
        this.clinical = clinical;
    }

    // ---- formulary ---------------------------------------------------------------------------

    @Transactional
    public Drug createDrug(DrugInput in) {
        TenantContext.Tenant t = TenantContext.require();
        UUID id;
        try {
            id = jdbc.sql("""
                    INSERT INTO drugs (org_id, generic_name, strength, form, unit, ppb_code, atc_code, controlled, unit_price, reorder_level, active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), in.genericName().trim(), blank(in.strength()), in.form().trim(), in.unit() == null ? "unit" : in.unit().trim(), blank(in.ppbCode()),
                            blank(in.atcCode()), Boolean.TRUE.equals(in.controlled()), in.unitPrice() == null ? BigDecimal.ZERO : in.unitPrice(),
                            in.reorderLevel() == null ? BigDecimal.ZERO : in.reorderLevel(), in.active() == null || in.active())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("drug_exists", "That product is already in the formulary.");
        }
        audit.record("drug.create", "drug", id, null, null, Map.of("name", in.genericName().trim()));
        return drug(id);
    }

    @Transactional
    public Drug updateDrug(UUID id, DrugInput in) {
        TenantContext.Tenant t = TenantContext.require();
        Drug before = drug(id);
        if (in.controlled() != null && in.controlled() != before.controlled()) {
            Long onHand = jdbc.sql("SELECT count(*) FROM stock_batches WHERE org_id = ? AND drug_id = ? AND quantity > 0").params(t.orgId(), id).query(Long.class).single();
            if (onHand > 0) {
                throw ApiException.conflict("stock_exists", "Change the controlled status only when no stock is on hand.");
            }
        }
        try {
            jdbc.sql("""
                    UPDATE drugs SET generic_name = ?, strength = ?, form = ?, unit = ?, ppb_code = ?, atc_code = ?, controlled = ?, unit_price = ?,
                           reorder_level = ?, active = ? WHERE org_id = ? AND id = ?""")
                    .params(in.genericName().trim(), blank(in.strength()), in.form().trim(), in.unit() == null ? before.unit() : in.unit().trim(), blank(in.ppbCode()),
                            blank(in.atcCode()), in.controlled() == null ? before.controlled() : in.controlled(), in.unitPrice() == null ? before.unitPrice() : in.unitPrice(),
                            in.reorderLevel() == null ? before.reorderLevel() : in.reorderLevel(), in.active() == null ? before.active() : in.active(), t.orgId(), id).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("drug_exists", "That product is already in the formulary.");
        }
        audit.record("drug.update", "drug", id, null, null, Map.of());
        return drug(id);
    }

    @Transactional(readOnly = true)
    public Slice<Drug> drugs(String q, boolean activeOnly, String cursor, Integer limit) {
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(TenantContext.require().orgId()));
        StringBuilder sql = new StringBuilder(DRUG_SQL + " WHERE org_id = ?");
        if (activeOnly) {
            sql.append(" AND active");
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND lower(generic_name) LIKE ?");
            p.add("%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%");
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (lower(generic_name), id) > (?, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY lower(generic_name), id LIMIT ?");
        p.add(size + 1);
        List<Drug> rows = jdbc.sql(sql.toString()).params(p.toArray()).query(PharmacyService::drugMap).list();
        boolean more = rows.size() > size;
        List<Drug> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).genericName().toLowerCase(), "i", page.get(page.size() - 1).id().toString())) : null;
        return new Slice<>(page, next);
    }

    private static final String DRUG_SQL = "SELECT id, generic_name, strength, form, unit, ppb_code, atc_code, controlled, unit_price, reorder_level, active FROM drugs";

    private Drug drug(UUID id) {
        return jdbc.sql(DRUG_SQL + " WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id).query(PharmacyService::drugMap).optional()
                .orElseThrow(() -> ApiException.notFound("Drug"));
    }

    private static Drug drugMap(ResultSet rs, int n) throws SQLException {
        return new Drug(rs.getObject("id", UUID.class), rs.getString("generic_name"), rs.getString("strength"), rs.getString("form"), rs.getString("unit"),
                rs.getString("ppb_code"), rs.getString("atc_code"), rs.getBoolean("controlled"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("reorder_level"),
                rs.getBoolean("active"));
    }

    // ---- stock -------------------------------------------------------------------------------

    @Transactional
    public Batch receive(Receipt in) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(in.facilityId());
        Drug d = drug(in.drugId());
        if (!d.active()) {
            throw ApiException.conflict("drug_inactive", "That product is retired from the formulary.");
        }
        if (!in.expiryDate().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("already_expired", "That batch is already expired and cannot be received into stock.");
        }
        // The same batch arriving again adds to it, but only if it is the same batch (same expiry).
        UUID existing = jdbc.sql("SELECT id FROM stock_batches WHERE org_id = ? AND facility_id = ? AND drug_id = ? AND batch_no = ? FOR UPDATE")
                .params(t.orgId(), in.facilityId(), in.drugId(), in.batchNo().trim()).query(UUID.class).optional().orElse(null);
        UUID batchId;
        if (existing != null) {
            LocalDate expiry = jdbc.sql("SELECT expiry_date FROM stock_batches WHERE id = ?").param(existing).query(LocalDate.class).single();
            if (!expiry.equals(in.expiryDate())) {
                throw ApiException.conflict("batch_expiry_mismatch", "Batch " + in.batchNo() + " is already in stock with expiry " + expiry + ".");
            }
            jdbc.sql("UPDATE stock_batches SET quantity = quantity + ? WHERE id = ?").params(in.quantity(), existing).update();
            batchId = existing;
        } else {
            batchId = jdbc.sql("""
                    INSERT INTO stock_batches (org_id, facility_id, drug_id, batch_no, expiry_date, quantity, unit_cost, supplier)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                    .params(t.orgId(), in.facilityId(), in.drugId(), in.batchNo().trim(), in.expiryDate(), in.quantity(), in.unitCost() == null ? BigDecimal.ZERO : in.unitCost(),
                            blank(in.supplier()))
                    .query(UUID.class).single();
        }
        movement(t, in.facilityId(), in.drugId(), batchId, in.quantity(), "RECEIPT", null, null, null, null);
        audit.record("stock.receive", "drug", in.drugId(), in.facilityId(), null, Map.of("batch", in.batchNo().trim(), "quantity", in.quantity().toPlainString()));
        return batch(batchId);
    }

    @Transactional
    public Batch adjust(Adjustment in) {
        TenantContext.Tenant t = TenantContext.require();
        var row = jdbc.sql("SELECT facility_id, drug_id, quantity FROM stock_batches WHERE org_id = ? AND id = ? FOR UPDATE").params(t.orgId(), in.batchId())
                .query((rs, n) -> new Object[] {rs.getObject("facility_id", UUID.class), rs.getObject("drug_id", UUID.class), rs.getBigDecimal("quantity")})
                .optional().orElseThrow(() -> ApiException.notFound("Batch"));
        UUID facilityId = (UUID) row[0];
        t.requireFacility(facilityId);
        BigDecimal delta = in.delta();
        if (delta.signum() == 0) {
            throw ApiException.badRequest("zero_adjustment", "An adjustment must change the quantity.");
        }
        if ("WRITE_OFF".equals(in.reason()) && delta.signum() > 0) {
            throw ApiException.badRequest("write_off_direction", "A write-off reduces stock.");
        }
        if (((BigDecimal) row[2]).add(delta).signum() < 0) {
            throw ApiException.conflict("insufficient_stock", "That would take the batch below zero (on hand " + row[2] + ").");
        }
        jdbc.sql("UPDATE stock_batches SET quantity = quantity + ? WHERE id = ?").params(delta, in.batchId()).update();
        movement(t, facilityId, (UUID) row[1], in.batchId(), delta, in.reason(), null, null, in.note().trim(), null);
        audit.record("stock.adjust", "drug", row[1], facilityId, in.note().trim(), Map.of("batch", in.batchId().toString(), "delta", delta.toPlainString(), "reason", in.reason()));
        return batch(in.batchId());
    }

    @Transactional(readOnly = true)
    public Slice<StockLine> stock(UUID facilityId, String q, boolean belowReorderOnly, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(facilityId, t.orgId(), t.orgId()));
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.generic_name, d.strength, d.form, d.controlled, d.reorder_level,
                       coalesce(sum(b.quantity) FILTER (WHERE b.expiry_date >= current_date), 0) AS usable,
                       coalesce(sum(b.quantity) FILTER (WHERE b.expiry_date < current_date), 0) AS expired
                  FROM drugs d LEFT JOIN stock_batches b ON b.org_id = d.org_id AND b.drug_id = d.id AND b.facility_id = ?
                 WHERE d.org_id = ? AND d.active""");
        p.remove(2);
        if (q != null && !q.isBlank()) {
            sql.append(" AND lower(d.generic_name) LIKE ?");
            p.add("%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%");
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (lower(d.generic_name), d.id) > (?, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" GROUP BY d.id");
        if (belowReorderOnly) {
            sql.append(" HAVING coalesce(sum(b.quantity) FILTER (WHERE b.expiry_date >= current_date), 0) <= d.reorder_level AND d.reorder_level > 0");
        }
        sql.append(" ORDER BY lower(d.generic_name), d.id LIMIT ?");
        p.add(size + 1);
        List<StockLine> rows = jdbc.sql(sql.toString()).params(p.toArray())
                .query((rs, n) -> new StockLine(rs.getObject("id", UUID.class), rs.getString("generic_name"), rs.getString("strength"), rs.getString("form"),
                        rs.getBoolean("controlled"), rs.getBigDecimal("usable"), rs.getBigDecimal("expired"), rs.getBigDecimal("reorder_level"),
                        rs.getBigDecimal("reorder_level").signum() > 0 && rs.getBigDecimal("usable").compareTo(rs.getBigDecimal("reorder_level")) <= 0)).list();
        boolean more = rows.size() > size;
        List<StockLine> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).genericName().toLowerCase(), "i", page.get(page.size() - 1).drugId().toString())) : null;
        return new Slice<>(page, next);
    }

    @Transactional(readOnly = true)
    public List<Batch> batches(UUID facilityId, UUID drugId, Integer expiringWithinDays) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), facilityId));
        StringBuilder sql = new StringBuilder(BATCH_SQL + " WHERE b.org_id = ? AND b.facility_id = ? AND b.quantity > 0");
        if (drugId != null) {
            sql.append(" AND b.drug_id = ?");
            p.add(drugId);
        }
        if (expiringWithinDays != null) {
            sql.append(" AND b.expiry_date <= ?");
            p.add(LocalDate.now().plusDays(expiringWithinDays));
        }
        sql.append(" ORDER BY b.expiry_date, b.id LIMIT 500");
        return jdbc.sql(sql.toString()).params(p.toArray()).query(PharmacyService::batchMap).list();
    }

    @Transactional(readOnly = true)
    public Slice<Movement> movements(UUID facilityId, UUID drugId, boolean controlledOnly, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), facilityId));
        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.batch_id, m.drug_id, d.generic_name || coalesce(' ' || d.strength, '') AS drug_name, m.delta, m.reason, m.ref_type, m.ref_id,
                       m.note, m.practitioner_id, m.witness_id, m.at
                  FROM stock_movements m JOIN drugs d ON d.org_id = m.org_id AND d.id = m.drug_id WHERE m.org_id = ? AND m.facility_id = ?""");
        if (drugId != null) {
            sql.append(" AND m.drug_id = ?");
            p.add(drugId);
        }
        if (controlledOnly) {
            sql.append(" AND d.controlled");
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND m.id < ?");
            p.add(Long.parseLong(after.get("i")));
        }
        sql.append(" ORDER BY m.id DESC LIMIT ?");
        p.add(size + 1);
        List<Movement> rows = jdbc.sql(sql.toString()).params(p.toArray())
                .query((rs, n) -> new Movement(rs.getLong("id"), rs.getObject("batch_id", UUID.class), rs.getObject("drug_id", UUID.class), rs.getString("drug_name"),
                        rs.getBigDecimal("delta"), rs.getString("reason"), rs.getString("ref_type"), rs.getObject("ref_id", UUID.class), rs.getString("note"),
                        rs.getObject("practitioner_id", UUID.class), rs.getObject("witness_id", UUID.class), rs.getObject("at", OffsetDateTime.class).toInstant())).list();
        boolean more = rows.size() > size;
        List<Movement> page = more ? rows.subList(0, size) : rows;
        return new Slice<>(page, more ? Slice.encode(Map.of("i", Long.toString(page.get(page.size() - 1).id()))) : null);
    }

    private static final String BATCH_SQL = """
            SELECT b.id, b.facility_id, b.drug_id, d.generic_name || coalesce(' ' || d.strength, '') AS drug_name, b.batch_no, b.expiry_date, b.quantity, b.unit_cost,
                   b.supplier, b.expiry_date < current_date AS expired
              FROM stock_batches b JOIN drugs d ON d.org_id = b.org_id AND d.id = b.drug_id""";

    private Batch batch(UUID id) {
        return jdbc.sql(BATCH_SQL + " WHERE b.org_id = ? AND b.id = ?").params(TenantContext.require().orgId(), id).query(PharmacyService::batchMap).single();
    }

    private static Batch batchMap(ResultSet rs, int n) throws SQLException {
        return new Batch(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getObject("drug_id", UUID.class), rs.getString("drug_name"),
                rs.getString("batch_no"), rs.getObject("expiry_date", LocalDate.class), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost"), rs.getString("supplier"),
                rs.getBoolean("expired"));
    }

    private void movement(TenantContext.Tenant t, UUID facilityId, UUID drugId, UUID batchId, BigDecimal delta, String reason, String refType, UUID refId, String note, UUID witness) {
        jdbc.sql("""
                INSERT INTO stock_movements (org_id, facility_id, drug_id, batch_id, delta, reason, ref_type, ref_id, note, practitioner_id, witness_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(t.orgId(), facilityId, drugId, batchId, delta, reason, refType, refId, note, t.practitionerId(), witness).update();
    }

    // ---- dispensing --------------------------------------------------------------------------

    /** Medication orders waiting at this facility, oldest first within urgency. */
    @Transactional(readOnly = true)
    public Slice<PendingOrder> queue(UUID facilityId, String cursor, Integer limit) {
        TenantContext.Tenant t = TenantContext.require();
        t.requireFacility(facilityId);
        int size = Slice.limit(limit);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), facilityId));
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.encounter_id, o.patient_id, pt.given_name || ' ' || pt.family_name AS patient_name, o.drug_name, o.drug_id, o.dose, o.frequency,
                       o.duration_days, o.quantity, o.dispensed_quantity, o.status, o.priority, o.created_at
                  FROM orders o JOIN patients pt ON pt.org_id = o.org_id AND pt.id = o.patient_id
                 WHERE o.org_id = ? AND o.facility_id = ? AND o.kind = 'MEDICATION' AND o.status IN ('ORDERED', 'IN_PROGRESS')""");
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (o.created_at, o.id) > (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY o.created_at, o.id LIMIT ?");
        p.add(size + 1);
        List<PendingOrder> rows = jdbc.sql(sql.toString()).params(p.toArray())
                .query((rs, n) -> new PendingOrder(rs.getObject("id", UUID.class), rs.getObject("encounter_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getString("patient_name"), rs.getString("drug_name"), rs.getObject("drug_id", UUID.class), rs.getString("dose"), rs.getString("frequency"),
                        (Integer) rs.getObject("duration_days"), rs.getBigDecimal("quantity"), rs.getBigDecimal("dispensed_quantity"), rs.getString("status"),
                        rs.getString("priority"), rs.getObject("created_at", OffsetDateTime.class).toInstant())).list();
        boolean more = rows.size() > size;
        List<PendingOrder> page = more ? rows.subList(0, size) : rows;
        String next = more ? Slice.encode(Map.of("t", page.get(page.size() - 1).orderedAt().toString(), "i", page.get(page.size() - 1).orderId().toString())) : null;
        return new Slice<>(page, next);
    }

    @Transactional
    public Dispensing dispense(DispenseInput in) {
        TenantContext.Tenant t = TenantContext.require();
        // Serialise concurrent dispensing of the same order.
        var o = jdbc.sql("""
                SELECT facility_id, patient_id, kind, status, drug_id, drug_name, quantity, dispensed_quantity, allergy_override_reason
                  FROM orders WHERE org_id = ? AND id = ? FOR UPDATE""").params(t.orgId(), in.orderId())
                .query((rs, n) -> new Object[] {rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("kind"), rs.getString("status"),
                        rs.getObject("drug_id", UUID.class), rs.getString("drug_name"), rs.getBigDecimal("quantity"), rs.getBigDecimal("dispensed_quantity")})
                .optional().orElseThrow(() -> ApiException.notFound("Order"));
        UUID facilityId = (UUID) o[0];
        UUID patientId = (UUID) o[1];
        t.requireFacility(facilityId);
        patients.require(patientId);
        if (!"MEDICATION".equals(o[2])) {
            throw ApiException.badRequest("not_medication", "That order is not a medication order.");
        }
        if (!List.of("ORDERED", "IN_PROGRESS").contains(o[3])) {
            throw ApiException.conflict("order_closed", "That order is " + o[3] + ".");
        }
        BigDecimal remaining = ((BigDecimal) o[6]).subtract((BigDecimal) o[7]);
        if (in.quantity().compareTo(remaining) > 0) {
            throw ApiException.conflict("over_dispense", "Only " + remaining + " remains to be dispensed on this order.");
        }
        UUID drugId = (UUID) o[4];
        if (drugId == null) {
            drugId = in.drugId();
            if (drugId == null) {
                throw ApiException.badRequest("drug_required", "This order names a drug but is not linked to a formulary product; choose one (drugId).");
            }
        } else if (in.drugId() != null && !in.drugId().equals(drugId)) {
            throw ApiException.badRequest("drug_mismatch", "That is not the product the prescriber chose.");
        }
        Drug drug = drug(drugId);
        if (!drug.active()) {
            throw ApiException.conflict("drug_inactive", "That product is retired from the formulary.");
        }
        // The prescriber's check ran against the name they typed; the pharmacist's runs against the product actually handed over.
        String overrideReason = blank(in.allergyOverrideReason());
        List<String> warnings = clinical.allergyWarnings(patientId, drug.genericName());
        if (!warnings.isEmpty() && (overrideReason == null || overrideReason.length() < 10)) {
            boolean prescriberOverrode = jdbc.sql("SELECT allergy_override_reason IS NOT NULL FROM orders WHERE id = ?").param(in.orderId()).query(Boolean.class).single();
            if (!prescriberOverrode) {
                throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "allergy_conflict",
                        "Recorded allergy: " + String.join("; ", warnings) + ". Give an allergyOverrideReason of at least 10 characters to dispense anyway.");
            }
        }
        UUID witness = in.witnessId();
        if (drug.controlled()) {
            if (witness == null || witness.equals(t.practitionerId())) {
                throw ApiException.badRequest("witness_required", "A controlled drug needs a second person, different from you, to witness the dispensing.");
            }
            Long ok = jdbc.sql("""
                    SELECT count(*) FROM practitioners p JOIN practitioner_facilities f ON f.practitioner_id = p.id
                     WHERE p.org_id = ? AND p.id = ? AND p.status = 'ACTIVE' AND f.facility_id = ?""").params(t.orgId(), witness, facilityId).query(Long.class).single();
            if (ok == 0) {
                throw ApiException.badRequest("witness_invalid", "The witness must be an active member of staff at this facility.");
            }
        } else {
            witness = null;
        }

        // First expiry first out, locking exactly the batches we draw from. Expired stock is never offered.
        var batches = jdbc.sql("""
                SELECT id, batch_no, expiry_date, quantity, unit_cost FROM stock_batches
                 WHERE org_id = ? AND facility_id = ? AND drug_id = ? AND quantity > 0 AND expiry_date >= ?
                 ORDER BY expiry_date, id FOR UPDATE""")
                .params(t.orgId(), facilityId, drugId, LocalDate.now(ZoneId.of("Africa/Nairobi")))
                .query((rs, n) -> new Object[] {rs.getObject("id", UUID.class), rs.getString("batch_no"), rs.getObject("expiry_date", LocalDate.class),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost")}).list();
        BigDecimal available = batches.stream().map(b -> (BigDecimal) b[3]).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(in.quantity()) < 0) {
            throw ApiException.conflict("insufficient_stock", "Only " + available.stripTrailingZeros().toPlainString() + " usable in stock.");
        }
        UUID dispensingId = jdbc.sql("""
                INSERT INTO dispensings (org_id, facility_id, order_id, patient_id, drug_id, quantity, dispensed_by, witness_id, allergy_override_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(t.orgId(), facilityId, in.orderId(), patientId, drugId, in.quantity(), t.practitionerId(), witness, warnings.isEmpty() ? null : overrideReason)
                .query(UUID.class).single();
        BigDecimal left = in.quantity();
        List<Source> sources = new ArrayList<>();
        for (Object[] b : batches) {
            if (left.signum() == 0) {
                break;
            }
            BigDecimal take = left.min((BigDecimal) b[3]);
            jdbc.sql("UPDATE stock_batches SET quantity = quantity - ? WHERE id = ?").params(take, b[0]).update();
            jdbc.sql("INSERT INTO dispensing_lines (org_id, dispensing_id, batch_id, quantity, unit_cost) VALUES (?, ?, ?, ?, ?)")
                    .params(t.orgId(), dispensingId, b[0], take, b[4]).update();
            movement(t, facilityId, drugId, (UUID) b[0], take.negate(), "DISPENSE", "dispensing", dispensingId, null, witness);
            sources.add(new Source((UUID) b[0], (String) b[1], (LocalDate) b[2], take));
            left = left.subtract(take);
        }
        BigDecimal newDispensed = ((BigDecimal) o[7]).add(in.quantity());
        String status = newDispensed.compareTo((BigDecimal) o[6]) >= 0 ? "COMPLETED" : "IN_PROGRESS";
        jdbc.sql("UPDATE orders SET dispensed_quantity = ?, status = ?, drug_id = ?, version = version + 1, updated_at = now() WHERE org_id = ? AND id = ?")
                .params(newDispensed, status, drugId, t.orgId(), in.orderId()).update();
        audit.record("pharmacy.dispense", "patient", patientId, facilityId, warnings.isEmpty() ? null : overrideReason,
                Map.of("order", in.orderId().toString(), "drug", drug.genericName(), "quantity", in.quantity().toPlainString(), "controlled", drug.controlled()));
        return new Dispensing(dispensingId, in.orderId(), patientId, drugId, drug.genericName(), in.quantity(), t.practitionerId(), witness, Instant.now(), sources,
                ((BigDecimal) o[6]).subtract(newDispensed), status);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
