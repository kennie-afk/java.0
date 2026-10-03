package com.hms.reporting;

import static com.hms.reporting.ReportDefinitionModels.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.Permissions;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.reporting.Measures.Measure;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report definitions chosen from the fixed measure catalogue. Counts only (no patient is named). These are the
 * organisation's own reports: they are not the Ministry of Health's official forms and carry no official definitions.
 */
@Service
public class ReportDefinitionService {

    static final String NOTE = "Operational counts from this system. Not an official Ministry of Health return; the definitions are this organisation's own.";
    private static final int MAX_DAYS = 366;

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final ObjectMapper json;

    public ReportDefinitionService(JdbcClient jdbc, AuditService audit, ObjectMapper json) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.json = json;
    }

    public List<MeasureInfo> measures() {
        return Measures.ALL.stream().map(m -> new MeasureInfo(m.code(), m.label(), m.description(), m.filter().name(), m.filterRequired(), m.disaggregations())).toList();
    }

    // ---- definitions -------------------------------------------------------------------------

    @Transactional
    public Definition create(DefinitionInput in) {
        TenantContext.Tenant t = TenantContext.require();
        validate(t, in);
        UUID id;
        try {
            id = jdbc.sql("INSERT INTO report_definitions (org_id, code, name, description, elements, dhis2, active, created_by) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?) RETURNING id")
                    .params(t.orgId(), in.code(), in.name().trim(), blank(in.description()), write(normalise(in.elements())), dhis2Json(in), in.active() == null || in.active(), t.practitionerId())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("report_exists", "A report with that code already exists.");
        }
        audit.record("report.definition.create", "report_definition", id, null, null, Map.of("code", in.code()));
        return get(id);
    }

    @Transactional
    public Definition update(UUID id, DefinitionInput in) {
        TenantContext.Tenant t = TenantContext.require();
        get(id);
        validate(t, in);
        try {
            jdbc.sql("UPDATE report_definitions SET code = ?, name = ?, description = ?, elements = ?::jsonb, dhis2 = ?::jsonb, active = ?, version = version + 1 WHERE org_id = ? AND id = ?")
                    .params(in.code(), in.name().trim(), blank(in.description()), write(normalise(in.elements())), dhis2Json(in), in.active() == null || in.active(), t.orgId(), id).update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("report_exists", "A report with that code already exists.");
        }
        audit.record("report.definition.update", "report_definition", id, null, null, Map.of("code", in.code()));
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<Definition> list(boolean activeOnly) {
        return jdbc.sql("SELECT id, code, name, description, elements, dhis2, active FROM report_definitions WHERE org_id = ?" + (activeOnly ? " AND active" : "") + " ORDER BY name LIMIT 500")
                .params(TenantContext.require().orgId()).query((rs, n) -> map(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("description"),
                        rs.getString("elements"), rs.getString("dhis2"), rs.getBoolean("active"))).list();
    }

    @Transactional(readOnly = true)
    public Definition get(UUID id) {
        return jdbc.sql("SELECT id, code, name, description, elements, dhis2, active FROM report_definitions WHERE org_id = ? AND id = ?").params(TenantContext.require().orgId(), id)
                .query((rs, n) -> map(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("description"), rs.getString("elements"), rs.getString("dhis2"),
                        rs.getBoolean("active"))).optional().orElseThrow(() -> ApiException.notFound("Report"));
    }

    private void validate(TenantContext.Tenant t, DefinitionInput in) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (Element e : in.elements()) {
            Measure m = Measures.find(e.measure()).orElseThrow(() -> ApiException.badRequest("unknown_measure", "'" + e.measure() + "' is not a measure this system offers."));
            if (!codes.add(e.code())) {
                throw ApiException.badRequest("duplicate_element", "Element code " + e.code() + " is used twice.");
            }
            String disagg = e.disaggregation() == null ? "NONE" : e.disaggregation();
            if (!m.disaggregations().contains(disagg)) {
                throw ApiException.badRequest("disaggregation_unsupported", m.code() + " cannot be split by " + disagg + ".");
            }
            boolean hasFilter = e.filter() != null && !e.filter().isBlank();
            if (m.filter() == Measures.Filter.NONE && hasFilter) {
                throw ApiException.badRequest("filter_unsupported", m.code() + " takes no filter.");
            }
            if (m.filterRequired() && !hasFilter) {
                throw ApiException.badRequest("filter_required", m.code() + " needs a filter (" + m.filter() + ").");
            }
            if (m.filter() == Measures.Filter.PROGRAMME && hasFilter) {
                if (!e.filter().toUpperCase().matches("HIV|TB|HYPERTENSION|DIABETES|ASTHMA|EPILEPSY")) {
                    throw ApiException.badRequest("programme_unknown", "Unknown programme " + e.filter() + ".");
                }
                if (e.filter().equalsIgnoreCase("HIV") && !t.can(Permissions.PROGRAMMES_HIV)) {
                    throw ApiException.forbidden("Reporting on HIV care needs the HIV programme permission.");
                }
            }
        }
        if (in.dhis2OrgUnits() != null) {
            for (Map.Entry<UUID, String> o : in.dhis2OrgUnits().entrySet()) {
                t.requireFacility(o.getKey());
                if (o.getValue() == null || !o.getValue().matches("^[A-Za-z][A-Za-z0-9]{10}$")) {
                    throw ApiException.badRequest("dhis2_org_unit", "A DHIS2 organisation unit identifier is 11 characters and starts with a letter.");
                }
            }
        }
    }

    private List<Element> normalise(List<Element> elements) {
        return elements.stream().map(e -> new Element(e.code(), e.label().trim(), e.measure(), e.disaggregation() == null ? "NONE" : e.disaggregation(),
                e.filter() == null || e.filter().isBlank() ? null : e.filter().trim().toUpperCase(), e.dhis2DataElement(), e.dhis2Options())).toList();
    }

    private String dhis2Json(DefinitionInput in) {
        if (in.dhis2DataSet() == null && (in.dhis2OrgUnits() == null || in.dhis2OrgUnits().isEmpty())) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataSet", in.dhis2DataSet());
        m.put("orgUnits", in.dhis2OrgUnits() == null ? Map.of() : in.dhis2OrgUnits());
        return write(m);
    }

    private Definition map(UUID id, String code, String name, String description, String elements, String dhis2, boolean active) {
        try {
            List<Element> els = json.readValue(elements, new TypeReference<List<Element>>() {});
            String dataSet = null;
            Map<UUID, String> units = null;
            if (dhis2 != null) {
                Map<String, Object> d = json.readValue(dhis2, new TypeReference<Map<String, Object>>() {});
                dataSet = (String) d.get("dataSet");
                units = json.convertValue(d.get("orgUnits"), new TypeReference<Map<UUID, String>>() {});
            }
            return new Definition(id, code, name, description, els, dataSet, units, active);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored report definition is unreadable: " + id, e);
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- running -----------------------------------------------------------------------------

    @Transactional
    public RunResult run(UUID id, UUID facilityId, LocalDate from, LocalDate to) {
        TenantContext.Tenant t = TenantContext.require();
        Definition d = get(id);
        t.requireFacility(facilityId);
        if (!d.active()) {
            throw ApiException.conflict("report_inactive", "That report is switched off.");
        }
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("bad_range", "Give a from and to date, with to on or after from.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw ApiException.badRequest("range_too_long", "A report covers at most " + MAX_DAYS + " days.");
        }
        Map<String, Object> fac = jdbc.sql("SELECT name, timezone FROM facilities WHERE org_id = ? AND id = ?").params(t.orgId(), facilityId).query().singleRow();
        String tz = (String) fac.get("timezone");
        ZoneId zone = ZoneId.of(tz);
        Timestamp start = Timestamp.from(from.atStartOfDay(zone).toInstant());
        Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
        List<Cell> cells = new ArrayList<>();
        for (Element e : d.elements()) {
            Measure m = Measures.find(e.measure()).orElseThrow(() -> ApiException.badRequest("unknown_measure", "Measure " + e.measure() + " is no longer offered."));
            if (m.filter() == Measures.Filter.PROGRAMME && "HIV".equalsIgnoreCase(e.filter()) && !t.can(Permissions.PROGRAMMES_HIV)) {
                throw ApiException.forbidden("This report includes HIV care, which needs the HIV programme permission.");
            }
            cells.addAll(count(t, e, m, facilityId, from, to, start, end));
        }
        audit.record("report.run", "report_definition", id, facilityId, null, Map.of("code", d.code(), "from", from.toString(), "to", to.toString()));
        return new RunResult(d.id(), d.code(), d.name(), facilityId, (String) fac.get("name"), from, to, tz, cells, NOTE);
    }

    private List<Cell> count(TenantContext.Tenant t, Element e, Measure m, UUID facilityId, LocalDate from, LocalDate to, Timestamp start, Timestamp end) {
        String disagg = e.disaggregation() == null ? "NONE" : e.disaggregation();
        String cat = Measures.categoryExpr(disagg, m.timeExpr());
        StringBuilder sql = new StringBuilder("SELECT " + cat + " AS cat, " + m.countExpr() + " AS n FROM " + m.from() + " WHERE x.org_id = ? AND " + m.facilityExpr() + " = ? AND "
                + m.timeExpr() + " >= ? AND " + m.timeExpr() + " < ?");
        List<Object> p = new ArrayList<>(List.of(t.orgId(), facilityId));
        if (m.timeIsDate()) {
            p.add(from);
            p.add(to.plusDays(1));
        } else {
            p.add(start);
            p.add(end);
        }
        if (!m.where().isEmpty()) {
            sql.append(" AND ").append(m.where());
            if (m.where().contains("?")) {
                p.add(e.filter().toUpperCase());
            }
        }
        boolean hasFilter = e.filter() != null && !e.filter().isBlank();
        if (m.filter() == Measures.Filter.ICD_PREFIX && hasFilter) {
            // The prefix was restricted to letters, digits, dot, dash at validation, so it holds no LIKE wildcard.
            sql.append(" AND d.icd11_code LIKE ?");
            p.add(e.filter().toUpperCase() + "%");
        }
        if (m.filter() == Measures.Filter.VACCINE && hasFilter) {
            sql.append(" AND x.vaccine = ?");
            p.add(e.filter().toUpperCase());
        }
        sql.append(" GROUP BY 1");
        Map<String, Long> got = new LinkedHashMap<>();
        jdbc.sql(sql.toString()).params(p.toArray()).query((rs, n) -> got.put(rs.getString("cat"), rs.getLong("n"))).list();
        List<Cell> out = new ArrayList<>();
        for (String option : Measures.OPTIONS.get(disagg)) {
            out.add(new Cell(e.code(), e.label(), option, got.getOrDefault(option, 0L)));
        }
        return out;
    }

    // ---- export ------------------------------------------------------------------------------

    @Transactional
    public String csv(UUID id, UUID facilityId, LocalDate from, LocalDate to) {
        RunResult r = run(id, facilityId, from, to);
        StringBuilder sb = new StringBuilder("report,facility,from,to,element_code,element,category,value\r\n");
        for (Cell c : r.cells()) {
            sb.append(esc(r.code())).append(',').append(esc(r.facilityName())).append(',').append(from).append(',').append(to).append(',').append(esc(c.element())).append(',')
                    .append(esc(c.label())).append(',').append(esc(c.category())).append(',').append(c.value()).append("\r\n");
        }
        audit.record("report.export", "report_definition", id, facilityId, null, Map.of("format", "csv", "from", from.toString(), "to", to.toString()));
        return sb.toString();
    }

    /**
     * A DHIS2 data value set (the JSON of the dataValueSets API: dataSet, completeDate, period, orgUnit, dataValues of
     * dataElement, categoryOptionCombo, value). Only whole calendar months are exported, as period "yyyyMM", and only
     * elements the definition has mapped to a DHIS2 data element are included. The identifiers are the organisation's own.
     */
    @Transactional
    public Map<String, Object> dhis2(UUID id, UUID facilityId, LocalDate from, LocalDate to) {
        Definition d = get(id);
        if (d.dhis2DataSet() == null || d.dhis2OrgUnits() == null || !d.dhis2OrgUnits().containsKey(facilityId)) {
            throw ApiException.conflict("dhis2_not_mapped", "This report has no DHIS2 data set, or no organisation unit for this facility.");
        }
        if (from == null || to == null || !from.equals(YearMonth.from(from).atDay(1)) || !to.equals(YearMonth.from(from).atEndOfMonth())) {
            throw ApiException.badRequest("dhis2_period", "A DHIS2 export covers one whole calendar month: from the 1st to the last day of the same month.");
        }
        RunResult r = run(id, facilityId, from, to);
        Map<String, Element> byCode = new LinkedHashMap<>();
        d.elements().forEach(e -> byCode.put(e.code(), e));
        List<Map<String, Object>> values = new ArrayList<>();
        for (Cell c : r.cells()) {
            Element e = byCode.get(c.element());
            if (e.dhis2DataElement() == null) {
                continue;
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("dataElement", e.dhis2DataElement());
            String combo = e.dhis2Options() == null ? null : e.dhis2Options().get(c.category());
            if (combo != null) {
                v.put("categoryOptionCombo", combo);
            } else if (!"Total".equals(c.category())) {
                // A split element needs every category mapped, or the value would be filed against the wrong cell.
                throw ApiException.badRequest("dhis2_option_unmapped", "Element " + e.code() + " has no DHIS2 category option combination for '" + c.category() + "'.");
            }
            v.put("value", String.valueOf(c.value()));
            values.add(v);
        }
        if (values.isEmpty()) {
            throw ApiException.conflict("dhis2_nothing_mapped", "No element of this report is mapped to a DHIS2 data element.");
        }
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("dataSet", d.dhis2DataSet());
        set.put("completeDate", LocalDate.now().toString());
        set.put("period", String.format("%04d%02d", from.getYear(), from.getMonthValue()));
        set.put("orgUnit", d.dhis2OrgUnits().get(facilityId));
        set.put("dataValues", values);
        audit.record("report.export", "report_definition", id, facilityId, null, Map.of("format", "dhis2", "from", from.toString(), "to", to.toString()));
        return set;
    }

    /** Quotes a CSV field and defuses a leading formula character, since labels are free text and the file is opened in a spreadsheet. */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
