package com.hms.registry;

import static com.hms.registry.PatientModels.*;

import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.PossibleDuplicateException;
import com.hms.platform.web.Slice;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The master patient index. Registration refuses to create a second record for a person who is
 * already there unless a human confirms it, because duplicate charts are the root of lost histories,
 * double billing and wrong-patient errors.
 */
@Service
public class PatientService {

    /** Below this a match is noise; above it a clerk must look before creating a new record. */
    static final double DUPLICATE_THRESHOLD = 0.7;

    private final JdbcClient jdbc;
    private final AuditService audit;

    public PatientService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ---- registration ------------------------------------------------------------------------

    @Transactional
    public Patient register(CreatePatient in) {
        TenantContext.Tenant tenant = TenantContext.require();
        requireFacility(tenant, in.facilityId());
        Demographics d = clean(in.demographics());
        List<IdentifierInput> identifiers = in.identifiers() == null ? List.of() : in.identifiers().stream().map(this::cleanIdentifier).toList();

        List<Match> matches = findMatches(d, identifiers, null);
        boolean exactIdentifier = matches.stream().anyMatch(m -> "IDENTIFIER".equals(m.reason()));
        if (exactIdentifier) {
            // The same national identifier is the same person. Never a judgement call, never overridable.
            throw new PossibleDuplicateException(matches);
        }
        if (!matches.isEmpty() && !in.confirmNotDuplicate()) {
            throw new PossibleDuplicateException(matches);
        }

        UUID id = jdbc.sql("""
                INSERT INTO patients (org_id, registered_facility_id, given_name, other_names, family_name, sex, birth_date,
                                      birth_date_estimated, phone, email, county, sub_county, address_line, marital_status, occupation)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(tenant.orgId(), in.facilityId(), d.givenName(), d.otherNames(), d.familyName(), d.sex(), d.birthDate(),
                        Boolean.TRUE.equals(d.birthDateEstimated()), d.phone(), d.email(), d.county(), d.subCounty(), d.addressLine(),
                        d.maritalStatus(), d.occupation())
                .query(UUID.class).single();

        for (IdentifierInput i : identifiers) {
            insertIdentifier(tenant, id, i.system(), i.value(), null);
        }
        insertIdentifier(tenant, id, "MRN", nextMrn(tenant, in.facilityId()), in.facilityId());
        if (in.contacts() != null) {
            for (ContactInput c : in.contacts()) {
                jdbc.sql("INSERT INTO patient_contacts (org_id, patient_id, relationship, full_name, phone, is_next_of_kin) VALUES (?, ?, ?, ?, ?, ?)")
                        .params(tenant.orgId(), id, c.relationship().trim(), c.fullName().trim(), phone(c.phone()), c.nextOfKin() == null || c.nextOfKin()).update();
            }
        }
        audit.record("patient.create", "patient", id, in.facilityId(), null,
                Map.of("duplicateOverride", !matches.isEmpty()));
        return load(id);
    }

    /** Looks for people who may already be this patient, for the registration screen as the clerk types. */
    @Transactional(readOnly = true)
    public List<Match> duplicates(Demographics raw, List<IdentifierInput> identifiers) {
        return findMatches(clean(raw), identifiers == null ? List.of() : identifiers.stream().map(this::cleanIdentifier).toList(), null);
    }

    private List<Match> findMatches(Demographics d, List<IdentifierInput> identifiers, UUID exclude) {
        TenantContext.Tenant tenant = TenantContext.require();
        Map<UUID, Match> found = new LinkedHashMap<>();
        for (IdentifierInput i : identifiers) {
            jdbc.sql("""
                    SELECT p.id, p.given_name, p.family_name, p.birth_date, p.sex, p.phone, m.value AS mrn
                      FROM patient_identifiers i JOIN patients p ON p.org_id = i.org_id AND p.id = i.patient_id
                      LEFT JOIN patient_identifiers m ON m.patient_id = p.id AND m.system = 'MRN'
                     WHERE i.org_id = ? AND i.system = ? AND i.value = ? AND p.merged_into IS NULL""")
                    .params(tenant.orgId(), i.system(), i.value())
                    .query((rs, n) -> match(rs, 1.0, "IDENTIFIER")).list()
                    .forEach(m -> found.putIfAbsent(m.id(), m));
        }
        String name = (d.givenName() + " " + (d.otherNames() == null ? "" : d.otherNames() + " ") + d.familyName()).toLowerCase();
        // The trigram operator uses the index; similarity() then scores the few rows it returns.
        jdbc.sql("""
                SELECT p.id, p.given_name, p.family_name, p.birth_date, p.sex, p.phone, m.value AS mrn,
                       similarity(p.search_text, ?) AS sim
                  FROM patients p LEFT JOIN patient_identifiers m ON m.patient_id = p.id AND m.system = 'MRN'
                 WHERE p.org_id = ? AND p.merged_into IS NULL AND p.search_text % ?
                 ORDER BY sim DESC LIMIT 20""")
                .params(name, tenant.orgId(), name)
                .query((rs, n) -> {
                    double sim = rs.getDouble("sim");
                    boolean sameBirth = d.birthDate().equals(rs.getObject("birth_date", java.time.LocalDate.class));
                    String samePhone = rs.getString("phone");
                    boolean phoneMatch = d.phone() != null && d.phone().equals(samePhone);
                    double score = 0;
                    String reason = null;
                    if (sameBirth && sim >= 0.5) {
                        score = Math.min(0.99, sim + 0.3);
                        reason = "NAME_AND_BIRTH_DATE";
                    } else if (phoneMatch && sim >= 0.35) {
                        score = Math.min(0.95, sim + 0.35);
                        reason = "NAME_AND_PHONE";
                    }
                    return reason == null ? null : match(rs, score, reason);
                }).list().stream().filter(m -> m != null && m.score() >= DUPLICATE_THRESHOLD)
                .forEach(m -> found.putIfAbsent(m.id(), m));
        if (exclude != null) {
            found.remove(exclude);
        }
        return found.values().stream().limit(5).toList();
    }

    private Match match(ResultSet rs, double score, String reason) throws SQLException {
        return new Match(rs.getObject("id", UUID.class), rs.getString("given_name"), rs.getString("family_name"),
                rs.getObject("birth_date", java.time.LocalDate.class), rs.getString("sex"), rs.getString("phone"), score, reason, rs.getString("mrn"));
    }

    // ---- reading -----------------------------------------------------------------------------

    /**
     * Opens one patient. Every open is audited. A restricted record additionally needs the
     * restricted-access permission AND a stated reason, which the audit trail keeps.
     */
    @Transactional
    public Patient open(UUID id, String reason) {
        TenantContext.Tenant tenant = TenantContext.require();
        Patient patient = load(id);
        if (patient.restricted()) {
            if (!tenant.can(Permissions.PATIENTS_RESTRICTED)) {
                throw ApiException.forbidden("This record is restricted.");
            }
            if (reason == null || reason.trim().length() < 10) {
                throw ApiException.badRequest("reason_required", "Opening a restricted record needs a reason of at least 10 characters.");
            }
        }
        audit.record("patient.read", "patient", id, patient.registeredFacilityId(), patient.restricted() ? reason.trim() : null, Map.of());
        return patient;
    }

    @Transactional(readOnly = true)
    public Slice<Summary> search(String q, String cursor, Integer limit) {
        TenantContext.Tenant tenant = TenantContext.require();
        int size = Slice.limit(limit);
        String term = q == null ? "" : q.trim().toLowerCase();
        Map<String, String> after = Slice.decode(cursor);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.given_name, p.family_name, p.sex, p.birth_date, p.phone, p.restricted, p.search_text, m.value AS mrn
                  FROM patients p LEFT JOIN patient_identifiers m ON m.patient_id = p.id AND m.system = 'MRN'
                 WHERE p.org_id = ? AND p.merged_into IS NULL""");
        params.add(tenant.orgId());
        if (!term.isEmpty()) {
            // A name fragment, or an identifier (MRN, national ID, SHA number...) typed exactly.
            sql.append(" AND (p.search_text LIKE ? OR EXISTS (SELECT 1 FROM patient_identifiers i WHERE i.patient_id = p.id AND lower(i.value) = ?)")
                    .append(" OR p.phone = ?)");
            params.add("%" + term.replace("%", "").replace("_", "") + "%");
            params.add(term);
            params.add(phoneOrNull(term));
        }
        if (after != null) {
            sql.append(" AND (p.search_text, p.id) > (?, ?::uuid)");
            params.add(after.get("t"));
            params.add(after.get("i"));
        }
        sql.append(" ORDER BY p.search_text, p.id LIMIT ?");
        params.add(size + 1);
        List<Object[]> rows = jdbc.sql(sql.toString()).params(params.toArray())
                .query((rs, n) -> new Object[] {new Summary(rs.getObject("id", UUID.class), rs.getString("given_name"), rs.getString("family_name"),
                        rs.getString("sex"), rs.getObject("birth_date", java.time.LocalDate.class), rs.getString("phone"),
                        rs.getBoolean("restricted"), rs.getString("mrn")), rs.getString("search_text")}).list();
        boolean more = rows.size() > size;
        List<Object[]> page = more ? rows.subList(0, size) : rows;
        String next = null;
        if (more) {
            Object[] last = page.get(page.size() - 1);
            next = Slice.encode(Map.of("t", (String) last[1], "i", ((Summary) last[0]).id().toString()));
        }
        return new Slice<>(page.stream().map(r -> (Summary) r[0]).toList(), next);
    }

    private String phoneOrNull(String term) {
        try {
            return Phones.normalise(term);
        } catch (IllegalArgumentException e) {
            return "-";
        }
    }

    // ---- change ------------------------------------------------------------------------------

    @Transactional
    public Patient update(UUID id, UpdatePatient in) {
        TenantContext.Tenant tenant = TenantContext.require();
        Patient before = load(id);
        if (before.mergedInto() != null) {
            throw ApiException.conflict("merged", "This record was merged into another and can no longer be edited.");
        }
        Demographics d = clean(in.demographics());
        int updated = jdbc.sql("""
                UPDATE patients SET given_name = ?, other_names = ?, family_name = ?, sex = ?, birth_date = ?, birth_date_estimated = ?,
                       phone = ?, email = ?, county = ?, sub_county = ?, address_line = ?, marital_status = ?, occupation = ?,
                       version = version + 1, updated_at = now()
                 WHERE org_id = ? AND id = ? AND version = ?""")
                .params(d.givenName(), d.otherNames(), d.familyName(), d.sex(), d.birthDate(), Boolean.TRUE.equals(d.birthDateEstimated()),
                        d.phone(), d.email(), d.county(), d.subCounty(), d.addressLine(), d.maritalStatus(), d.occupation(),
                        tenant.orgId(), id, in.version())
                .update();
        if (updated == 0) {
            throw ApiException.conflict("stale_version", "Someone else changed this record. Reload it and try again.");
        }
        // Which fields changed, not their values: the audit trail must not become a second copy of the chart.
        List<String> changed = new ArrayList<>();
        diff(changed, "givenName", before.givenName(), d.givenName());
        diff(changed, "otherNames", before.otherNames(), d.otherNames());
        diff(changed, "familyName", before.familyName(), d.familyName());
        diff(changed, "sex", before.sex(), d.sex());
        diff(changed, "birthDate", before.birthDate(), d.birthDate());
        diff(changed, "phone", before.phone(), d.phone());
        diff(changed, "email", before.email(), d.email());
        diff(changed, "county", before.county(), d.county());
        diff(changed, "subCounty", before.subCounty(), d.subCounty());
        diff(changed, "addressLine", before.addressLine(), d.addressLine());
        diff(changed, "maritalStatus", before.maritalStatus(), d.maritalStatus());
        diff(changed, "occupation", before.occupation(), d.occupation());
        audit.record("patient.update", "patient", id, before.registeredFacilityId(), null, Map.of("fields", String.join(",", changed)));
        return load(id);
    }

    @Transactional
    public Identifier addIdentifier(UUID id, IdentifierInput in) {
        TenantContext.Tenant tenant = TenantContext.require();
        Patient p = load(id);
        IdentifierInput clean = cleanIdentifier(in);
        List<Match> clash = findMatches(new Demographics("x", null, "x", "UNKNOWN", java.time.LocalDate.now(), null, null, null, null, null, null, null, null),
                List.of(clean), id);
        if (!clash.isEmpty()) {
            throw new PossibleDuplicateException(clash);
        }
        Identifier created = insertIdentifier(tenant, id, clean.system(), clean.value(), null);
        audit.record("patient.identifier.add", "patient", id, p.registeredFacilityId(), null, Map.of("system", clean.system()));
        return created;
    }

    /** Folds a duplicate record into the surviving one. Nothing is deleted: the old record points at the survivor. */
    @Transactional
    public Patient merge(UUID duplicateId, MergeRequest in) {
        TenantContext.Tenant tenant = TenantContext.require();
        if (duplicateId.equals(in.survivorId())) {
            throw ApiException.badRequest("same_patient", "A record cannot be merged into itself.");
        }
        Patient duplicate = load(duplicateId);
        Patient survivor = load(in.survivorId());
        if (duplicate.mergedInto() != null || survivor.mergedInto() != null) {
            throw ApiException.conflict("already_merged", "One of these records has already been merged.");
        }
        // Identifiers move across, except where the survivor already holds the same one.
        jdbc.sql("""
                UPDATE patient_identifiers i SET patient_id = ?
                 WHERE i.org_id = ? AND i.patient_id = ?
                   AND NOT EXISTS (SELECT 1 FROM patient_identifiers s WHERE s.patient_id = ? AND s.system = i.system AND s.value = i.value)
                   AND i.system <> 'MRN'""").params(in.survivorId(), tenant.orgId(), duplicateId, in.survivorId()).update();
        jdbc.sql("UPDATE patient_contacts SET patient_id = ? WHERE org_id = ? AND patient_id = ?").params(in.survivorId(), tenant.orgId(), duplicateId).update();
        jdbc.sql("UPDATE patients SET merged_into = ?, active = false, version = version + 1, updated_at = now() WHERE org_id = ? AND id = ?")
                .params(in.survivorId(), tenant.orgId(), duplicateId).update();
        audit.record("patient.merge", "patient", duplicateId, duplicate.registeredFacilityId(), in.reason().trim(), Map.of("survivor", in.survivorId().toString()));
        audit.record("patient.merge.receive", "patient", in.survivorId(), survivor.registeredFacilityId(), in.reason().trim(), Map.of("merged", duplicateId.toString()));
        return load(in.survivorId());
    }

    /** Records a death, once. Called when an admission ends in death; the patient is never deleted or deactivated by it. */
    @Transactional
    public void markDeceased(UUID id, java.time.Instant at, String reason) {
        TenantContext.Tenant tenant = TenantContext.require();
        Patient p = load(id);
        if (p.deceasedAt() != null) {
            return;
        }
        jdbc.sql("UPDATE patients SET deceased_at = ?, version = version + 1, updated_at = now() WHERE org_id = ? AND id = ?")
                .params(java.sql.Timestamp.from(at), tenant.orgId(), id).update();
        audit.record("patient.deceased", "patient", id, p.registeredFacilityId(), reason, Map.of());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private void requireFacility(TenantContext.Tenant tenant, UUID facilityId) {
        if (!tenant.facilityIds().contains(facilityId)) {
            throw ApiException.forbidden("You do not work at that facility.");
        }
    }

    private Identifier insertIdentifier(TenantContext.Tenant tenant, UUID patientId, String system, String value, UUID facilityId) {
        UUID id = jdbc.sql("INSERT INTO patient_identifiers (org_id, patient_id, system, value, facility_id) VALUES (?, ?, ?, ?, ?) RETURNING id")
                .params(tenant.orgId(), patientId, system, value, facilityId).query(UUID.class).single();
        return new Identifier(id, system, value, facilityId);
    }

    private String nextMrn(TenantContext.Tenant tenant, UUID facilityId) {
        jdbc.sql("INSERT INTO facility_counters (org_id, facility_id, name) VALUES (?, ?, 'MRN') ON CONFLICT DO NOTHING").params(tenant.orgId(), facilityId).update();
        long n = jdbc.sql("UPDATE facility_counters SET next_value = next_value + 1 WHERE facility_id = ? AND name = 'MRN' RETURNING next_value - 1")
                .param(facilityId).query(Long.class).single();
        String prefix = jdbc.sql("SELECT coalesce(mfl_code, upper(left(replace(id::text, '-', ''), 4))) FROM facilities WHERE id = ?")
                .param(facilityId).query(String.class).single();
        return String.format("%s-%06d", prefix, n);
    }

    Patient load(UUID id) {
        TenantContext.Tenant tenant = TenantContext.require();
        Patient base = jdbc.sql("""
                SELECT id, registered_facility_id, given_name, other_names, family_name, sex, birth_date, birth_date_estimated, phone, email,
                       county, sub_county, address_line, marital_status, occupation, nationality, deceased_at, active, restricted,
                       merged_into, version, created_at, updated_at
                  FROM patients WHERE org_id = ? AND id = ?""").params(tenant.orgId(), id)
                .query((rs, n) -> new Patient(rs.getObject("id", UUID.class), rs.getObject("registered_facility_id", UUID.class),
                        rs.getString("given_name"), rs.getString("other_names"), rs.getString("family_name"), rs.getString("sex"),
                        rs.getObject("birth_date", java.time.LocalDate.class), rs.getBoolean("birth_date_estimated"), rs.getString("phone"),
                        rs.getString("email"), rs.getString("county"), rs.getString("sub_county"), rs.getString("address_line"),
                        rs.getString("marital_status"), rs.getString("occupation"), rs.getString("nationality"),
                        instant(rs.getObject("deceased_at", OffsetDateTime.class)), rs.getBoolean("active"), rs.getBoolean("restricted"),
                        rs.getObject("merged_into", UUID.class), rs.getInt("version"), instant(rs.getObject("created_at", OffsetDateTime.class)),
                        instant(rs.getObject("updated_at", OffsetDateTime.class)), List.of(), List.of()))
                .optional().orElseThrow(() -> ApiException.notFound("Patient"));
        List<Identifier> identifiers = jdbc.sql("SELECT id, system, value, facility_id FROM patient_identifiers WHERE patient_id = ? ORDER BY system, value").param(id)
                .query((rs, n) -> new Identifier(rs.getObject("id", UUID.class), rs.getString("system"), rs.getString("value"), rs.getObject("facility_id", UUID.class))).list();
        List<Contact> contacts = jdbc.sql("SELECT id, relationship, full_name, phone, is_next_of_kin FROM patient_contacts WHERE patient_id = ? ORDER BY relationship").param(id)
                .query((rs, n) -> new Contact(rs.getObject("id", UUID.class), rs.getString("relationship"), rs.getString("full_name"), rs.getString("phone"), rs.getBoolean("is_next_of_kin"))).list();
        return new Patient(base.id(), base.registeredFacilityId(), base.givenName(), base.otherNames(), base.familyName(), base.sex(), base.birthDate(),
                base.birthDateEstimated(), base.phone(), base.email(), base.county(), base.subCounty(), base.addressLine(), base.maritalStatus(),
                base.occupation(), base.nationality(), base.deceasedAt(), base.active(), base.restricted(), base.mergedInto(), base.version(),
                base.createdAt(), base.updatedAt(), identifiers, contacts);
    }

    private static java.time.Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }

    private static void diff(List<String> changed, String field, Object before, Object after) {
        if (!java.util.Objects.equals(before, after)) {
            changed.add(field);
        }
    }

    private String phone(String raw) {
        try {
            return Phones.normalise(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_phone", "That is not a Kenyan mobile number.");
        }
    }

    private static String trimOrNull(String s) {
        return s == null || s.isBlank() ? null : s.trim().replaceAll("\\s+", " ");
    }

    private Demographics clean(Demographics d) {
        return new Demographics(trimOrNull(d.givenName()), trimOrNull(d.otherNames()), trimOrNull(d.familyName()), d.sex(), d.birthDate(),
                d.birthDateEstimated(), phone(d.phone()), trimOrNull(d.email()), trimOrNull(d.county()), trimOrNull(d.subCounty()),
                trimOrNull(d.addressLine()), d.maritalStatus(), trimOrNull(d.occupation()));
    }

    private IdentifierInput cleanIdentifier(IdentifierInput i) {
        String value = i.value().trim().toUpperCase().replaceAll("\\s+", "");
        if ("NATIONAL_ID".equals(i.system()) && !value.matches("\\d{7,8}")) {
            throw ApiException.badRequest("invalid_identifier", "A Kenyan national ID is 7 or 8 digits.");
        }
        return new IdentifierInput(i.system(), value);
    }
}
