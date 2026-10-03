package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportDefinitionTest extends IntegrationTest {

    private UUID newPatient(Org org, String sex, String birth) throws Exception {
        Map<String, Object> p = patient(org.facilityId(), "Def", "Ault" + UUID.randomUUID().toString().substring(0, 6), birth);
        @SuppressWarnings("unchecked") Map<String, Object> demo = new java.util.LinkedHashMap<>((Map<String, Object>) p.get("demographics"));
        demo.put("sex", sex);
        p.put("demographics", demo);
        return UUID.fromString(create("/v1/patients", org.token(), p).get("id").asText());
    }

    private void visit(Org org, String doctor, UUID p, String code) throws Exception {
        String enc = create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "type", "OPD")).get("id").asText();
        create("/v1/clinical/encounters/" + enc + "/diagnoses", doctor, Map.of("icd11Code", code, "title", "Diagnosis " + code, "kind", "PRIMARY", "certainty", "CONFIRMED"));
        send(post("/v1/clinical/encounters/" + enc + "/close", doctor), 200);
    }

    private Map<String, Object> element(String code, String measure, String disaggregation, String filter) {
        Map<String, Object> e = new java.util.LinkedHashMap<>(Map.of("code", code, "label", "Element " + code, "measure", measure, "disaggregation", disaggregation));
        if (filter != null) {
            e.put("filter", filter);
        }
        return e;
    }

    private JsonNode cell(JsonNode run, String element, String category) {
        for (JsonNode c : run.get("cells")) {
            if (c.get("element").asText().equals(element) && c.get("category").asText().equals(category)) {
                return c;
            }
        }
        throw new AssertionError("no cell " + element + "/" + category);
    }

    @Test
    void aDefinitionCountsWhatHappenedSplitByCategoryAndExportsToCsv() throws Exception {
        Org org = newOrg("repdef");
        String doctor = userWithRole(org, "DOCTOR");
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        visit(org, doctor, newPatient(org, "FEMALE", today.minusYears(2).toString()), "CA40");
        visit(org, doctor, newPatient(org, "MALE", "1985-05-05"), "CA40");
        visit(org, doctor, newPatient(org, "MALE", "1950-01-01"), "1A00");
        JsonNode def = create("/v1/report-definitions", org.token(), Map.of("code", "MONTHLY", "name", "Monthly summary", "elements", List.of(
                element("OPD", "OPD_VISITS", "NONE", null), element("OPD_SEX", "OPD_VISITS", "SEX", null), element("OPD_AGE", "OPD_VISITS", "AGE_BAND", null),
                element("PNEUMONIA", "DIAGNOSED_CASES", "NONE", "ca40"))));
        String id = def.get("id").asText();
        String q = "?facilityId=" + org.facilityId() + "&from=" + today.minusDays(1) + "&to=" + today;
        JsonNode run = fetch("/v1/report-definitions/" + id + "/run" + q, org.token());
        assertThat(cell(run, "OPD", "Total").get("value").asLong()).isEqualTo(3);
        assertThat(cell(run, "OPD_SEX", "MALE").get("value").asLong()).isEqualTo(2);
        assertThat(cell(run, "OPD_SEX", "FEMALE").get("value").asLong()).isEqualTo(1);
        assertThat(cell(run, "OPD_SEX", "INTERSEX").get("value").asLong()).isZero();
        assertThat(cell(run, "OPD_AGE", "0-4").get("value").asLong()).isEqualTo(1);
        assertThat(cell(run, "OPD_AGE", "60+").get("value").asLong()).isEqualTo(1);
        assertThat(cell(run, "PNEUMONIA", "Total").get("value").asLong()).isEqualTo(2);
        assertThat(run.get("note").asText()).contains("Not an official Ministry of Health return");
        assertThat(run.toString()).doesNotContain("Ault");
        // A window with nothing in it is zeros, not an error.
        JsonNode empty = fetch("/v1/report-definitions/" + id + "/run?facilityId=" + org.facilityId() + "&from=2020-01-01&to=2020-01-31", org.token());
        assertThat(cell(empty, "OPD", "Total").get("value").asLong()).isZero();
        String csv = mvc.perform(get("/v1/report-definitions/" + id + "/export.csv" + q, org.token())).andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("report,facility,from,to,element_code,element,category,value").contains("\"OPD_SEX\",\"Element OPD_SEX\",\"MALE\",2");
        assertThat(fetch("/v1/audit/events?entityType=report_definition&entityId=" + id, org.token()).findValuesAsText("action")).contains("report.run", "report.export");
    }

    @Test
    void definitionsAreValidatedAgainstTheCatalogueAndCsvCellsCannotCarryFormulas() throws Exception {
        Org org = newOrg("repval");
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "BAD", "name", "Bad", "elements", List.of(element("A", "DROP_TABLE", "NONE", null))), 400);
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "BAD", "name", "Bad", "elements", List.of(element("A", "ADMISSIONS", "MODALITY", null))), 400);
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "BAD", "name", "Bad", "elements", List.of(element("A", "DIAGNOSED_CASES", "NONE", null))), 400);
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "BAD", "name", "Bad", "elements", List.of(element("A", "ADMISSIONS", "NONE", "X"))), 400);
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "BAD", "name", "Bad", "elements", List.of(element("A", "DIAGNOSED_CASES", "NONE", "CA40' OR 1=1"))), 400);
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "BAD", "name", "Bad", "elements", List.of(element("A", "ADMISSIONS", "NONE", null), element("A", "DISCHARGES", "NONE", null))), 400);
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "BAD", "name", "Bad", "elements", List.of()), 400);
        Map<String, Object> formula = new java.util.LinkedHashMap<>(element("F", "ADMISSIONS", "NONE", null));
        formula.put("label", "=HYPERLINK(\"http://x\")");
        String id = create("/v1/report-definitions", org.token(), Map.of("code", "FORMULA", "name", "Formula label", "elements", List.of(formula))).get("id").asText();
        String q = "?facilityId=" + org.facilityId() + "&from=2026-01-01&to=2026-01-31";
        String csv = mvc.perform(get("/v1/report-definitions/" + id + "/export.csv" + q, org.token())).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("\"'=HYPERLINK").doesNotContain(",\"=HYPERLINK");
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "FORMULA", "name", "Again", "elements", List.of(element("A", "ADMISSIONS", "NONE", null))), 409);
        // Running needs a sensible range.
        send(get("/v1/report-definitions/" + id + "/run?facilityId=" + org.facilityId() + "&from=2026-02-01&to=2026-01-01", org.token()), 400);
        send(get("/v1/report-definitions/" + id + "/run?facilityId=" + org.facilityId() + "&from=2024-01-01&to=2026-01-01", org.token()), 400);
        // Ordinary roles read and run reports but cannot define them.
        String nurse = userWithRole(org, "AUDITOR", "ADMINISTRATIVE");
        sendJson(post("/v1/report-definitions", nurse), Map.of("code", "NOPE", "name", "Nope", "elements", List.of(element("A", "ADMISSIONS", "NONE", null))), 403);
        assertThat(fetch("/v1/report-definitions", nurse)).hasSize(1);
    }

    @Test
    void theDhis2ExportFollowsTheDataValueSetShapeAndRefusesWhatItCannotMapSafely() throws Exception {
        Org org = newOrg("repdhis");
        String doctor = userWithRole(org, "DOCTOR");
        LocalDate lastMonthStart = YearMonth.now(ZoneId.of("Africa/Nairobi")).minusMonths(1).atDay(1);
        LocalDate lastMonthEnd = YearMonth.from(lastMonthStart).atEndOfMonth();
        Map<String, Object> total = new java.util.LinkedHashMap<>(element("OPD", "OPD_VISITS", "NONE", null));
        total.put("dhis2DataElement", "fbfJHSPpUQD");
        Map<String, Object> bySex = new java.util.LinkedHashMap<>(element("SEX", "OPD_VISITS", "SEX", null));
        bySex.put("dhis2DataElement", "cYeuwXTCPkU");
        bySex.put("dhis2Options", Map.of("MALE", "pq2XI5kz2BY", "FEMALE", "PT59n8BQbqM", "INTERSEX", "Prlt0C1RF0s", "UNKNOWN", "V6L425pT3A0"));
        Map<String, Object> bySexUnmapped = new java.util.LinkedHashMap<>(element("SEX2", "OPD_VISITS", "SEX", null));
        bySexUnmapped.put("dhis2DataElement", "Jtf34kNZhzP");
        String body = "{\"code\":\"D1\",\"name\":\"DHIS2 test\",\"dhis2DataSet\":\"BfMAe6Itzgt\",\"dhis2OrgUnits\":{\"" + org.facilityId() + "\":\"DiszpKrYNg8\"},\"elements\":";
        String mapped = json.writeValueAsString(List.of(total, bySex));
        JsonNode def = sendJson(post("/v1/report-definitions", org.token()), json.readValue(body + mapped + "}", Map.class), 201);
        String id = def.get("id").asText();
        String q = "?facilityId=" + org.facilityId() + "&from=" + lastMonthStart + "&to=" + lastMonthEnd;
        JsonNode set = fetch("/v1/report-definitions/" + id + "/export.dhis2" + q, org.token());
        assertThat(set.get("dataSet").asText()).isEqualTo("BfMAe6Itzgt");
        assertThat(set.get("orgUnit").asText()).isEqualTo("DiszpKrYNg8");
        assertThat(set.get("period").asText()).isEqualTo(String.format("%04d%02d", lastMonthStart.getYear(), lastMonthStart.getMonthValue()));
        assertThat(set.get("completeDate").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(set.get("dataValues")).hasSize(5);
        assertThat(set.get("dataValues").get(0).get("dataElement").asText()).isEqualTo("fbfJHSPpUQD");
        assertThat(set.get("dataValues").get(0).has("categoryOptionCombo")).isFalse();
        assertThat(set.get("dataValues").get(1).get("categoryOptionCombo").asText()).isEqualTo("pq2XI5kz2BY");
        assertThat(set.get("dataValues").get(0).get("value").asText()).isEqualTo("0");
        // Only a whole calendar month is exported.
        send(get("/v1/report-definitions/" + id + "/export.dhis2?facilityId=" + org.facilityId() + "&from=" + lastMonthStart + "&to=" + lastMonthStart.plusDays(5), org.token()), 400);
        // A split element with a category left unmapped is refused rather than filed against the wrong cell.
        String unmapped = json.writeValueAsString(List.of(bySexUnmapped));
        String id2 = sendJson(post("/v1/report-definitions", org.token()), json.readValue(body.replace("\"D1\"", "\"D2\"") + unmapped + "}", Map.class), 201).get("id").asText();
        send(get("/v1/report-definitions/" + id2 + "/export.dhis2" + q, org.token()), 400);
        // No mapping at all: conflict. A malformed identifier: rejected on save.
        String plain = create("/v1/report-definitions", org.token(), Map.of("code", "P1", "name", "Unmapped", "elements", List.of(element("A", "ADMISSIONS", "NONE", null)))).get("id").asText();
        send(get("/v1/report-definitions/" + plain + "/export.dhis2" + q, org.token()), 409);
        Map<String, Object> badUid = new java.util.LinkedHashMap<>(element("A", "ADMISSIONS", "NONE", null));
        badUid.put("dhis2DataElement", "not a uid!");
        sendJson(post("/v1/report-definitions", org.token()), Map.of("code", "P2", "name", "Bad uid", "elements", List.of(badUid)), 400);
    }

    @Test
    void everyMeasureRunsWithEverySupportedSplit() throws Exception {
        Org org = newOrg("repall");
        JsonNode measures = fetch("/v1/report-definitions/measures", org.token());
        assertThat(measures.size()).isGreaterThanOrEqualTo(8);
        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        int n = 0;
        for (JsonNode m : measures) {
            for (JsonNode d : m.get("disaggregations")) {
                String filter = switch (m.get("filter").asText()) {
                    case "ICD_PREFIX" -> "CA40";
                    case "PROGRAMME" -> "TB";
                    case "VACCINE" -> "BCG";
                    default -> null;
                };
                elements.add(element("E" + n++, m.get("code").asText(), d.asText(), filter));
            }
        }
        String id = create("/v1/report-definitions", org.token(), Map.of("code", "ALL", "name", "Everything", "elements", elements.subList(0, Math.min(elements.size(), 60)))).get("id").asText();
        JsonNode run = fetch("/v1/report-definitions/" + id + "/run?facilityId=" + org.facilityId() + "&from=2026-01-01&to=2026-12-31", org.token());
        assertThat(run.get("cells").size()).isGreaterThan(n);
        assertThat(run.get("cells").findValues("value")).allMatch(v -> v.asLong() == 0);
    }
}
