package com.hms.fhir;

import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.platform.web.Slice;
import com.hms.registry.PatientAccess;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A read-only FHIR R4 (4.0.1) view of the record: Patient, Encounter, Observation (vital signs and validated
 * laboratory results), MedicationRequest and AllergyIntolerance. It maps what this system holds; it does not claim
 * conformance to any national implementation guide, and it uses no national identifier system URIs (identifiers
 * carry a local urn). Every read is audited. A restricted record needs the access reason header; unvalidated lab
 * results are never exposed.
 */
@Service
public class FhirService {

    public record Page(List<Map<String, Object>> resources, String nextCursor) {}

    static final String IDENTIFIER_SYSTEM_PREFIX = "urn:hms:identifier:";
    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;

    public FhirService(JdbcClient jdbc, AuditService audit, PatientAccess patients) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
    }

    // ---- access ------------------------------------------------------------------------------

    /** The patient must be readable by the caller; a restricted record also needs a stated reason, which is audited. */
    private PatientAccess.Ref access(UUID patientId, String reason, String resourceType) {
        PatientAccess.Ref ref = patients.require(patientId);
        if (ref.restricted() && (reason == null || reason.isBlank() || reason.trim().length() < 5)) {
            throw ApiException.forbidden("This record is restricted. Send the X-Access-Reason header with a reason of at least 5 characters.");
        }
        audit.record("fhir.read", "patient", patientId, null, ref.restricted() ? reason.trim() : null, Map.of("resource", resourceType));
        return ref;
    }

    private static UUID uuid(String id, String what) {
        if (id == null || !UUID_RE.matcher(id).matches()) {
            throw ApiException.notFound(what);
        }
        return UUID.fromString(id);
    }

    private static int count(Integer requested) {
        return requested == null ? 20 : Math.max(1, Math.min(requested, Slice.MAX_LIMIT));
    }

    // ---- Patient -----------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> patient(String id, String reason) {
        UUID pid = uuid(id, "Patient");
        access(pid, reason, "Patient");
        Map<String, Object> p = jdbc.sql(PATIENT_SQL + " WHERE p.org_id = ? AND p.id = ?").params(TenantContext.require().orgId(), pid).query(this::patientMap).optional()
                .orElseThrow(() -> ApiException.notFound("Patient"));
        p.remove("_created");
        return p;
    }

    @Transactional
    public Page patients(String identifier, String name, String birthdate, String id, String cursor, Integer countParam) {
        TenantContext.Tenant t = TenantContext.require();
        int size = count(countParam);
        List<Object> p = new ArrayList<>(List.of(t.orgId()));
        // Search never returns restricted or merged-away records: a search has nowhere to state a reason.
        StringBuilder sql = new StringBuilder(PATIENT_SQL + " WHERE p.org_id = ? AND NOT p.restricted AND p.merged_into IS NULL");
        if (id != null && !id.isBlank()) {
            sql.append(" AND p.id = ?");
            p.add(uuid(id, "Patient"));
        }
        if (identifier != null && !identifier.isBlank()) {
            String system = null;
            String value = identifier.trim();
            int bar = value.indexOf('|');
            if (bar >= 0) {
                String sys = value.substring(0, bar);
                value = value.substring(bar + 1);
                if (!sys.isEmpty()) {
                    if (!sys.startsWith(IDENTIFIER_SYSTEM_PREFIX)) {
                        throw ApiException.badRequest("identifier_system", "Identifier systems here are of the form " + IDENTIFIER_SYSTEM_PREFIX + "<TYPE>.");
                    }
                    system = sys.substring(IDENTIFIER_SYSTEM_PREFIX.length());
                }
            }
            sql.append(" AND EXISTS (SELECT 1 FROM patient_identifiers i WHERE i.org_id = p.org_id AND i.patient_id = p.id AND i.value = ?");
            p.add(value);
            if (system != null) {
                sql.append(" AND i.system = ?");
                p.add(system);
            }
            sql.append(")");
        }
        if (name != null && !name.isBlank()) {
            sql.append(" AND lower(p.given_name || ' ' || p.family_name) LIKE ?");
            p.add("%" + name.trim().toLowerCase().replace("%", "").replace("_", "") + "%");
        }
        if (birthdate != null && !birthdate.isBlank()) {
            try {
                p.add(java.time.LocalDate.parse(birthdate));
            } catch (java.time.format.DateTimeParseException e) {
                throw ApiException.badRequest("birthdate", "birthdate must be a date like 1985-05-05.");
            }
            sql.append(" AND p.birth_date = ?");
        }
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql.append(" AND (p.created_at, p.id) < (?::timestamptz, ?::uuid)");
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT ?");
        p.add(size + 1);
        List<Map<String, Object>> rows = jdbc.sql(sql.toString()).params(p.toArray()).query(this::patientMap).list();
        audit.record("fhir.search", "patient", null, null, null, Map.of("resource", "Patient", "returned", Math.min(rows.size(), size)));
        return page(rows, size, "created", "id");
    }

    private static final String PATIENT_SQL = """
            SELECT p.id, p.given_name, p.other_names, p.family_name, p.sex, p.birth_date, p.phone, p.email, p.county, p.sub_county, p.address_line, p.nationality, p.deceased_at,
                   p.active, p.merged_into, p.version, p.updated_at, p.created_at,
                   (SELECT coalesce(json_agg(json_build_object('system', i.system, 'value', i.value) ORDER BY i.system, i.value), '[]'::json) FROM patient_identifiers i
                     WHERE i.org_id = p.org_id AND i.patient_id = p.id)::text AS identifiers
              FROM patients p""";

    private Map<String, Object> patientMap(ResultSet rs, int n) throws SQLException {
        Map<String, Object> r = resource("Patient", rs.getString("id"));
        r.put("meta", meta(rs.getInt("version"), rs.getObject("updated_at", OffsetDateTime.class)));
        r.put("_created", rs.getObject("created_at", OffsetDateTime.class).toInstant().toString());
        List<Map<String, Object>> ids = new ArrayList<>();
        try {
            for (var node : new com.fasterxml.jackson.databind.ObjectMapper().readTree(rs.getString("identifiers"))) {
                ids.add(obj("system", IDENTIFIER_SYSTEM_PREFIX + node.get("system").asText(), "value", node.get("value").asText()));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SQLException(e);
        }
        if (!ids.isEmpty()) {
            r.put("identifier", ids);
        }
        UUID mergedInto = rs.getObject("merged_into", UUID.class);
        r.put("active", rs.getBoolean("active") && mergedInto == null);
        List<String> given = new ArrayList<>(List.of(rs.getString("given_name")));
        if (rs.getString("other_names") != null) {
            given.addAll(List.of(rs.getString("other_names").trim().split("\\s+")));
        }
        r.put("name", List.of(obj("use", "official", "family", rs.getString("family_name"), "given", given)));
        List<Map<String, Object>> telecom = new ArrayList<>();
        if (rs.getString("phone") != null) {
            telecom.add(obj("system", "phone", "value", rs.getString("phone")));
        }
        if (rs.getString("email") != null) {
            telecom.add(obj("system", "email", "value", rs.getString("email")));
        }
        if (!telecom.isEmpty()) {
            r.put("telecom", telecom);
        }
        r.put("gender", switch (rs.getString("sex")) {
            case "MALE" -> "male";
            case "FEMALE" -> "female";
            case "INTERSEX" -> "other";
            default -> "unknown";
        });
        r.put("birthDate", rs.getObject("birth_date", java.time.LocalDate.class).toString());
        OffsetDateTime died = rs.getObject("deceased_at", OffsetDateTime.class);
        r.put(died == null ? "deceasedBoolean" : "deceasedDateTime", died == null ? (Object) false : died.toInstant().toString());
        Map<String, Object> addr = new LinkedHashMap<>();
        if (rs.getString("address_line") != null) {
            addr.put("line", List.of(rs.getString("address_line")));
        }
        if (rs.getString("sub_county") != null) {
            addr.put("district", rs.getString("sub_county"));
        }
        if (rs.getString("county") != null) {
            addr.put("state", rs.getString("county"));
        }
        addr.put("country", rs.getString("nationality"));
        r.put("address", List.of(addr));
        if (mergedInto != null) {
            r.put("link", List.of(obj("other", obj("reference", "Patient/" + mergedInto), "type", "replaced-by")));
        }
        return r;
    }

    // ---- Encounter ---------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> encounter(String id, String reason) {
        UUID eid = uuid(id, "Encounter");
        TenantContext.Tenant t = TenantContext.require();
        Map<String, Object> e = jdbc.sql(ENCOUNTER_SQL + " WHERE e.org_id = ? AND e.id = ? AND e.facility_id = ANY (?)").params(t.orgId(), eid, t.facilityIds().toArray(UUID[]::new))
                .query(this::encounterMap).optional().orElseThrow(() -> ApiException.notFound("Encounter"));
        access(UUID.fromString(((String) ((Map<?, ?>) e.get("subject")).get("reference")).substring("Patient/".length())), reason, "Encounter");
        return e;
    }

    @Transactional
    public Page encounters(String patient, String cursor, Integer countParam, String reason) {
        TenantContext.Tenant t = TenantContext.require();
        UUID pid = requirePatientParam(patient);
        access(pid, reason, "Encounter");
        int size = count(countParam);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), pid, t.facilityIds().toArray(UUID[]::new)));
        String sql = ENCOUNTER_SQL + " WHERE e.org_id = ? AND e.patient_id = ? AND e.facility_id = ANY (?)";
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql += " AND (e.started_at, e.id) < (?::timestamptz, ?::uuid)";
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        p.add(size + 1);
        List<Map<String, Object>> rows = jdbc.sql(sql + " ORDER BY e.started_at DESC, e.id DESC LIMIT ?").params(p.toArray()).query(this::encounterMap).list();
        return page(rows, size, "started", "id");
    }

    private static final String ENCOUNTER_SQL = "SELECT e.id, e.patient_id, e.encounter_type, e.status, e.chief_complaint, e.started_at, e.ended_at, e.version FROM encounters e";

    private Map<String, Object> encounterMap(ResultSet rs, int n) throws SQLException {
        Map<String, Object> r = resource("Encounter", rs.getString("id"));
        r.put("meta", meta(rs.getInt("version"), null));
        r.put("status", "OPEN".equals(rs.getString("status")) ? "in-progress" : "finished");
        String type = rs.getString("encounter_type");
        r.put("class", obj("system", "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code", switch (type) {
            case "ED" -> "EMER";
            case "IPD" -> "IMP";
            default -> "AMB";
        }, "display", switch (type) {
            case "ED" -> "emergency";
            case "IPD" -> "inpatient encounter";
            default -> "ambulatory";
        }));
        r.put("subject", obj("reference", "Patient/" + rs.getString("patient_id")));
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("start", rs.getObject("started_at", OffsetDateTime.class).toInstant().toString());
        if (rs.getObject("ended_at", OffsetDateTime.class) != null) {
            period.put("end", rs.getObject("ended_at", OffsetDateTime.class).toInstant().toString());
        }
        r.put("period", period);
        if (rs.getString("chief_complaint") != null) {
            r.put("reasonCode", List.of(obj("text", rs.getString("chief_complaint"))));
        }
        return r;
    }

    // ---- Observation -------------------------------------------------------------------------

    private record VitalDef(String key, String column, String loinc, String display, String unit, String ucum, boolean integer) {}

    /** LOINC codes for the vital signs; MUAC has no code here because none was verified, so it carries text only. */
    private static final List<VitalDef> VITALS = List.of(
            new VitalDef("temp", "temp_c", "8310-5", "Body temperature", "Cel", "Cel", false),
            new VitalDef("pulse", "pulse", "8867-4", "Heart rate", "/min", "/min", true),
            new VitalDef("resp", "resp_rate", "9279-1", "Respiratory rate", "/min", "/min", true),
            new VitalDef("spo2", "spo2", "59408-5", "Oxygen saturation in Arterial blood by Pulse oximetry", "%", "%", true),
            new VitalDef("weight", "weight_kg", "29463-7", "Body weight", "kg", "kg", false),
            new VitalDef("height", "height_cm", "8302-2", "Body height", "cm", "cm", false),
            new VitalDef("muac", "muac_cm", null, "Mid-upper arm circumference", "cm", "cm", false),
            new VitalDef("glucose", "glucose_mmol", "15074-8", "Glucose [Moles/volume] in Blood", "mmol/L", "mmol/L", false),
            new VitalDef("pain", "pain_score", "72514-3", "Pain severity - 0-10 verbal numeric rating [Score] - Reported", "{score}", "{score}", true));

    private static final String VITALS_SQL = """
            SELECT v.id, v.encounter_id, v.patient_id, v.recorded_at, v.temp_c, v.pulse, v.resp_rate, v.systolic, v.diastolic, v.spo2, v.weight_kg, v.height_cm, v.muac_cm, v.glucose_mmol, v.pain_score
              FROM vitals v LEFT JOIN clinical_retractions r ON r.org_id = v.org_id AND r.entity_type = 'vitals' AND r.entity_id = v.id""";

    private static final String LAB_SQL = """
            SELECT i.id, o.patient_id, o.encounter_id, i.validated_at, i.result_numeric, i.result_text, i.flag, lt.name, lt.loinc_code, lt.unit, lt.ref_low, lt.ref_high
              FROM lab_order_items i JOIN lab_orders o ON o.org_id = i.org_id AND o.id = i.order_id JOIN lab_tests lt ON lt.org_id = i.org_id AND lt.id = i.test_id""";

    @Transactional
    public Map<String, Object> observation(String id, String reason) {
        TenantContext.Tenant t = TenantContext.require();
        if (id != null && id.startsWith("lab-")) {
            UUID item = uuid(id.substring(4), "Observation");
            Map<String, Object> o = jdbc.sql(LAB_SQL + " WHERE i.org_id = ? AND i.id = ? AND i.status = 'VALIDATED' AND o.facility_id = ANY (?)")
                    .params(t.orgId(), item, t.facilityIds().toArray(UUID[]::new)).query(this::labMap).optional().orElseThrow(() -> ApiException.notFound("Observation"));
            requireLab(t);
            access(patientOf(o), reason, "Observation");
            return o;
        }
        if (id != null && id.startsWith("vit-")) {
            int last = id.lastIndexOf('-');
            if (last > 4) {
                UUID vid = uuid(id.substring(4, last), "Observation");
                String key = id.substring(last + 1);
                Map<String, Object> found = jdbc.sql(VITALS_SQL + " WHERE v.org_id = ? AND v.id = ? AND v.facility_id = ANY (?) AND r.id IS NULL").params(t.orgId(), vid, t.facilityIds().toArray(UUID[]::new))
                        .query((rs, n) -> vitalsMap(rs).stream().filter(m -> m.get("id").equals(id)).findFirst().orElse(null)).optional().orElse(null);
                if (found != null && key.length() > 0) {
                    access(patientOf(found), reason, "Observation");
                    return found;
                }
            }
        }
        throw ApiException.notFound("Observation");
    }

    @Transactional
    public Page observations(String patient, String category, String cursor, Integer countParam, String reason) {
        TenantContext.Tenant t = TenantContext.require();
        UUID pid = requirePatientParam(patient);
        if (category == null || !(category.equals("vital-signs") || category.equals("laboratory"))) {
            throw ApiException.badRequest("category_required", "category is required: vital-signs or laboratory.");
        }
        if (category.equals("laboratory")) {
            requireLab(t);
        }
        access(pid, reason, "Observation");
        int size = count(countParam);
        Map<String, String> after = Slice.decode(cursor);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), pid, t.facilityIds().toArray(UUID[]::new)));
        if (category.equals("laboratory")) {
            String sql = LAB_SQL + " WHERE i.org_id = ? AND o.patient_id = ? AND o.facility_id = ANY (?) AND i.status = 'VALIDATED'";
            if (after != null) {
                sql += " AND (i.validated_at, i.id) < (?::timestamptz, ?::uuid)";
                p.add(after.get("t"));
                p.add(after.get("i"));
            }
            p.add(size + 1);
            List<Map<String, Object>> rows = jdbc.sql(sql + " ORDER BY i.validated_at DESC, i.id DESC LIMIT ?").params(p.toArray()).query(this::labMap).list();
            return page(rows, size, "validated", "labid");
        }
        // One vitals recording can hold up to nine measurements, so a page is `count` recordings, not `count` observations.
        String sql = VITALS_SQL + " WHERE v.org_id = ? AND v.patient_id = ? AND v.facility_id = ANY (?) AND r.id IS NULL";
        if (after != null) {
            sql += " AND (v.recorded_at, v.id) < (?::timestamptz, ?::uuid)";
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        p.add(size + 1);
        List<List<Map<String, Object>>> groups = jdbc.sql(sql + " ORDER BY v.recorded_at DESC, v.id DESC LIMIT ?").params(p.toArray()).query((rs, n) -> {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("_t", rs.getObject("recorded_at", OffsetDateTime.class).toInstant().toString());
            marker.put("_i", rs.getString("id"));
            List<Map<String, Object>> list = new ArrayList<>(vitalsMap(rs));
            list.add(0, marker);
            return list;
        }).list();
        boolean more = groups.size() > size;
        List<Map<String, Object>> flat = new ArrayList<>();
        for (List<Map<String, Object>> g : groups.subList(0, Math.min(size, groups.size()))) {
            flat.addAll(g.subList(1, g.size()));
        }
        String next = more ? Slice.encode(Map.of("t", (String) groups.get(size - 1).get(0).get("_t"), "i", (String) groups.get(size - 1).get(0).get("_i"))) : null;
        return new Page(flat, next);
    }

    private static void requireLab(TenantContext.Tenant t) {
        if (!t.can(Permissions.LAB_READ)) {
            throw ApiException.forbidden("Laboratory results need the laboratory read permission.");
        }
    }

    private static UUID patientOf(Map<String, Object> observation) {
        return UUID.fromString(((String) ((Map<?, ?>) observation.get("subject")).get("reference")).substring("Patient/".length()));
    }

    private List<Map<String, Object>> vitalsMap(ResultSet rs) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        String vid = rs.getString("id");
        String at = rs.getObject("recorded_at", OffsetDateTime.class).toInstant().toString();
        for (VitalDef d : VITALS) {
            Object v = rs.getObject(d.column());
            if (v == null) {
                continue;
            }
            Map<String, Object> o = vitalBase("vit-" + vid + "-" + d.key(), rs, at);
            o.put("code", codeable(d.loinc(), d.display()));
            o.put("valueQuantity", quantity(v, d.unit(), d.ucum()));
            out.add(o);
        }
        Object sys = rs.getObject("systolic");
        if (sys != null) {
            Map<String, Object> o = vitalBase("vit-" + vid + "-bp", rs, at);
            o.put("code", codeable("85354-9", "Blood pressure panel with all children optional"));
            o.put("component", List.of(
                    obj("code", codeable("8480-6", "Systolic blood pressure"), "valueQuantity", quantity(sys, "mmHg", "mm[Hg]")),
                    obj("code", codeable("8462-4", "Diastolic blood pressure"), "valueQuantity", quantity(rs.getObject("diastolic"), "mmHg", "mm[Hg]"))));
            out.add(o);
        }
        return out;
    }

    private Map<String, Object> vitalBase(String id, ResultSet rs, String at) throws SQLException {
        Map<String, Object> o = resource("Observation", id);
        o.put("status", "final");
        o.put("category", List.of(obj("coding", List.of(obj("system", "http://terminology.hl7.org/CodeSystem/observation-category", "code", "vital-signs", "display", "Vital Signs")))));
        o.put("subject", obj("reference", "Patient/" + rs.getString("patient_id")));
        o.put("encounter", obj("reference", "Encounter/" + rs.getString("encounter_id")));
        o.put("effectiveDateTime", at);
        return o;
    }

    private Map<String, Object> labMap(ResultSet rs, int n) throws SQLException {
        Map<String, Object> o = resource("Observation", "lab-" + rs.getString("id"));
        o.put("status", "final");
        o.put("category", List.of(obj("coding", List.of(obj("system", "http://terminology.hl7.org/CodeSystem/observation-category", "code", "laboratory", "display", "Laboratory")))));
        Map<String, Object> code = new LinkedHashMap<>();
        if (rs.getString("loinc_code") != null) {
            code.put("coding", List.of(obj("system", "http://loinc.org", "code", rs.getString("loinc_code"))));
        }
        code.put("text", rs.getString("name"));
        o.put("code", code);
        o.put("subject", obj("reference", "Patient/" + rs.getString("patient_id")));
        if (rs.getString("encounter_id") != null) {
            o.put("encounter", obj("reference", "Encounter/" + rs.getString("encounter_id")));
        }
        o.put("effectiveDateTime", rs.getObject("validated_at", OffsetDateTime.class).toInstant().toString());
        if (rs.getBigDecimal("result_numeric") != null) {
            // The unit is the organisation's own text, so it is given as text and not claimed to be UCUM.
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("value", rs.getBigDecimal("result_numeric").stripTrailingZeros());
            if (rs.getString("unit") != null) {
                q.put("unit", rs.getString("unit"));
            }
            o.put("valueQuantity", q);
        } else {
            o.put("valueString", rs.getString("result_text"));
        }
        String flag = rs.getString("flag");
        if (flag != null) {
            o.put("interpretation", List.of(obj("coding", List.of(obj("system", "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation", "code", flag)))));
        }
        if (rs.getBigDecimal("ref_low") != null || rs.getBigDecimal("ref_high") != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (rs.getBigDecimal("ref_low") != null) {
                range.put("low", obj("value", rs.getBigDecimal("ref_low").stripTrailingZeros()));
            }
            if (rs.getBigDecimal("ref_high") != null) {
                range.put("high", obj("value", rs.getBigDecimal("ref_high").stripTrailingZeros()));
            }
            o.put("referenceRange", List.of(range));
        }
        return o;
    }

    // ---- MedicationRequest -------------------------------------------------------------------

    private static final String MED_SQL = """
            SELECT o.id, o.patient_id, o.encounter_id, o.status, o.priority, o.drug_name, o.dose, o.route, o.frequency, o.duration_days, o.quantity, o.instructions, o.created_at, o.version, d.atc_code
              FROM orders o LEFT JOIN drugs d ON d.org_id = o.org_id AND d.id = o.drug_id""";

    @Transactional
    public Map<String, Object> medicationRequest(String id, String reason) {
        UUID oid = uuid(id, "MedicationRequest");
        TenantContext.Tenant t = TenantContext.require();
        Map<String, Object> m = jdbc.sql(MED_SQL + " WHERE o.org_id = ? AND o.id = ? AND o.kind = 'MEDICATION' AND o.facility_id = ANY (?)")
                .params(t.orgId(), oid, t.facilityIds().toArray(UUID[]::new)).query(this::medMap).optional().orElseThrow(() -> ApiException.notFound("MedicationRequest"));
        access(patientOf(m), reason, "MedicationRequest");
        return m;
    }

    @Transactional
    public Page medicationRequests(String patient, String cursor, Integer countParam, String reason) {
        TenantContext.Tenant t = TenantContext.require();
        UUID pid = requirePatientParam(patient);
        access(pid, reason, "MedicationRequest");
        int size = count(countParam);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), pid, t.facilityIds().toArray(UUID[]::new)));
        String sql = MED_SQL + " WHERE o.org_id = ? AND o.patient_id = ? AND o.kind = 'MEDICATION' AND o.facility_id = ANY (?)";
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql += " AND (o.created_at, o.id) < (?::timestamptz, ?::uuid)";
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        p.add(size + 1);
        return page(jdbc.sql(sql + " ORDER BY o.created_at DESC, o.id DESC LIMIT ?").params(p.toArray()).query(this::medMap).list(), size, "authored", "id");
    }

    private Map<String, Object> medMap(ResultSet rs, int n) throws SQLException {
        Map<String, Object> r = resource("MedicationRequest", rs.getString("id"));
        r.put("meta", meta(rs.getInt("version"), null));
        r.put("status", switch (rs.getString("status")) {
            case "COMPLETED" -> "completed";
            case "CANCELLED" -> "cancelled";
            default -> "active";
        });
        r.put("intent", "order");
        r.put("priority", switch (rs.getString("priority")) {
            case "STAT" -> "stat";
            case "URGENT" -> "urgent";
            default -> "routine";
        });
        Map<String, Object> med = new LinkedHashMap<>();
        if (rs.getString("atc_code") != null) {
            med.put("coding", List.of(obj("system", "http://www.whocc.no/atc", "code", rs.getString("atc_code"))));
        }
        med.put("text", rs.getString("drug_name"));
        r.put("medicationCodeableConcept", med);
        r.put("subject", obj("reference", "Patient/" + rs.getString("patient_id")));
        r.put("encounter", obj("reference", "Encounter/" + rs.getString("encounter_id")));
        r.put("authoredOn", rs.getObject("created_at", OffsetDateTime.class).toInstant().toString());
        List<String> text = new ArrayList<>();
        for (String c : new String[] {"dose", "route", "frequency"}) {
            if (rs.getString(c) != null) {
                text.add(rs.getString(c));
            }
        }
        if (rs.getObject("duration_days") != null) {
            text.add("for " + rs.getInt("duration_days") + " days");
        }
        Map<String, Object> dosage = new LinkedHashMap<>();
        if (!text.isEmpty()) {
            dosage.put("text", String.join(" ", text));
        }
        if (rs.getString("route") != null) {
            dosage.put("route", obj("text", rs.getString("route")));
        }
        if (rs.getString("instructions") != null) {
            dosage.put("patientInstruction", rs.getString("instructions"));
        }
        if (!dosage.isEmpty()) {
            r.put("dosageInstruction", List.of(dosage));
        }
        if (rs.getBigDecimal("quantity") != null) {
            r.put("dispenseRequest", obj("quantity", obj("value", rs.getBigDecimal("quantity").stripTrailingZeros())));
        }
        return r;
    }

    // ---- AllergyIntolerance ------------------------------------------------------------------

    private static final String ALLERGY_SQL = "SELECT a.id, a.patient_id, a.substance, a.category, a.reaction, a.severity, a.status, a.created_at, a.updated_at FROM allergies a";

    @Transactional
    public Map<String, Object> allergy(String id, String reason) {
        UUID aid = uuid(id, "AllergyIntolerance");
        TenantContext.Tenant t = TenantContext.require();
        Map<String, Object> a = jdbc.sql(ALLERGY_SQL + " WHERE a.org_id = ? AND a.id = ?").params(t.orgId(), aid).query(this::allergyMap).optional()
                .orElseThrow(() -> ApiException.notFound("AllergyIntolerance"));
        access(UUID.fromString(((String) ((Map<?, ?>) a.get("patient")).get("reference")).substring("Patient/".length())), reason, "AllergyIntolerance");
        return a;
    }

    @Transactional
    public Page allergies(String patient, String cursor, Integer countParam, String reason) {
        TenantContext.Tenant t = TenantContext.require();
        UUID pid = requirePatientParam(patient);
        access(pid, reason, "AllergyIntolerance");
        int size = count(countParam);
        List<Object> p = new ArrayList<>(List.of(t.orgId(), pid));
        String sql = ALLERGY_SQL + " WHERE a.org_id = ? AND a.patient_id = ?";
        Map<String, String> after = Slice.decode(cursor);
        if (after != null) {
            sql += " AND (a.created_at, a.id) < (?::timestamptz, ?::uuid)";
            p.add(after.get("t"));
            p.add(after.get("i"));
        }
        p.add(size + 1);
        return page(jdbc.sql(sql + " ORDER BY a.created_at DESC, a.id DESC LIMIT ?").params(p.toArray()).query(this::allergyMap).list(), size, "recorded", "id");
    }

    private Map<String, Object> allergyMap(ResultSet rs, int n) throws SQLException {
        Map<String, Object> r = resource("AllergyIntolerance", rs.getString("id"));
        String status = rs.getString("status");
        String sys = "http://terminology.hl7.org/CodeSystem/allergyintolerance-";
        r.put("clinicalStatus", obj("coding", List.of(obj("system", sys + "clinical", "code", "ACTIVE".equals(status) ? "active" : "inactive"))));
        if ("ENTERED_IN_ERROR".equals(status)) {
            r.put("verificationStatus", obj("coding", List.of(obj("system", sys + "verification", "code", "entered-in-error"))));
        }
        switch (rs.getString("category")) {
            case "DRUG" -> r.put("category", List.of("medication"));
            case "FOOD" -> r.put("category", List.of("food"));
            case "ENVIRONMENT" -> r.put("category", List.of("environment"));
            default -> { }
        }
        String severity = rs.getString("severity");
        r.put("criticality", severity.equals("SEVERE") || severity.equals("LIFE_THREATENING") ? "high" : "low");
        r.put("code", obj("text", rs.getString("substance")));
        r.put("patient", obj("reference", "Patient/" + rs.getString("patient_id")));
        r.put("recordedDate", rs.getObject("created_at", OffsetDateTime.class).toInstant().toString());
        Map<String, Object> reaction = new LinkedHashMap<>();
        reaction.put("manifestation", List.of(obj("text", rs.getString("reaction") == null ? "Reaction not described" : rs.getString("reaction"))));
        reaction.put("severity", switch (severity) {
            case "MILD" -> "mild";
            case "MODERATE" -> "moderate";
            default -> "severe";
        });
        r.put("reaction", List.of(reaction));
        return r;
    }

    // ---- helpers -----------------------------------------------------------------------------

    private UUID requirePatientParam(String patient) {
        if (patient == null || patient.isBlank()) {
            throw ApiException.badRequest("patient_required", "The patient search parameter is required.");
        }
        String v = patient.startsWith("Patient/") ? patient.substring("Patient/".length()) : patient;
        return uuid(v, "Patient");
    }

    private static Page page(List<Map<String, Object>> rows, int size, String timeKey, String idKey) {
        boolean more = rows.size() > size;
        List<Map<String, Object>> keep = more ? rows.subList(0, size) : rows;
        String next = null;
        if (more) {
            Map<String, Object> last = keep.get(keep.size() - 1);
            next = Slice.encode(Map.of("t", cursorTime(last, timeKey), "i", cursorId(last, idKey)));
        }
        List<Map<String, Object>> out = new ArrayList<>(keep);
        out.forEach(r -> r.remove("_created"));
        return new Page(out, next);
    }

    // The cursor position is read back from the last resource: its id, and the timestamp field the query sorts on.
    private static String cursorId(Map<String, Object> r, String idKey) {
        String id = (String) r.get("id");
        return id.startsWith("lab-") ? id.substring(4) : id;
    }

    private static String cursorTime(Map<String, Object> r, String key) {
        return switch (key) {
            case "created" -> (String) r.get("_created");
            case "started" -> (String) ((Map<?, ?>) r.get("period")).get("start");
            case "validated" -> (String) r.get("effectiveDateTime");
            case "authored" -> (String) r.get("authoredOn");
            case "recorded" -> (String) r.get("recordedDate");
            default -> throw new IllegalStateException(key);
        };
    }

    static Map<String, Object> resource(String type, String id) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resourceType", type);
        r.put("id", id);
        return r;
    }

    static Map<String, Object> meta(int version, OffsetDateTime updated) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versionId", String.valueOf(version));
        if (updated != null) {
            m.put("lastUpdated", updated.toInstant().toString());
        }
        return m;
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> codeable(String loinc, String display) {
        Map<String, Object> c = new LinkedHashMap<>();
        if (loinc != null) {
            c.put("coding", List.of(obj("system", "http://loinc.org", "code", loinc, "display", display)));
        }
        c.put("text", display);
        return c;
    }

    private static Map<String, Object> quantity(Object value, String unit, String ucum) {
        Object v = value instanceof BigDecimal b ? b.stripTrailingZeros() : value;
        return obj("value", v, "unit", unit, "system", "http://unitsofmeasure.org", "code", ucum);
    }
}
