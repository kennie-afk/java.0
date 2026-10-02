package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportingTest extends IntegrationTest {

    private UUID newPatient(Org org, String birth) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Rep", "Ort" + UUID.randomUUID().toString().substring(0, 6), birth)).get("id").asText());
    }

    private String visit(Org org, String doctor, UUID p, String code, String title) throws Exception {
        String enc = create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "type", "OPD")).get("id").asText();
        create("/v1/clinical/encounters/" + enc + "/diagnoses", doctor, Map.of("icd11Code", code, "title", title, "kind", "PRIMARY", "certainty", "CONFIRMED"));
        send(post("/v1/clinical/encounters/" + enc + "/close", doctor), 200);
        return enc;
    }

    @Test
    void theOverviewCountsWhatActuallyHappenedAndNamesNoOne() throws Exception {
        Org org = newOrg("report");
        String doctor = userWithRole(org, "DOCTOR");
        String tech1 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String tech2 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        UUID adult = newPatient(org, "1985-05-05");
        UUID child = newPatient(org, today.minusYears(2).toString());
        String e1 = visit(org, doctor, adult, "CA40", "Pneumonia");
        visit(org, doctor, child, "CA40", "Pneumonia");
        visit(org, doctor, newPatient(org, "1950-01-01"), "1A00", "Cholera");

        // Money: one invoice of 1000, 600 paid in cash.
        String inv = create("/v1/billing/invoices", org.token(), Map.of("facilityId", org.facilityId().toString(), "patientId", adult.toString(), "encounterId", e1)).get("id").asText();
        sendJson(post("/v1/billing/invoices/" + inv + "/lines", org.token()), Map.of("description", "Consultation", "unitPrice", 1000, "quantity", 1), 200);
        send(post("/v1/billing/invoices/" + inv + "/issue", org.token()), 200);
        sendJson(post("/v1/billing/invoices/" + inv + "/payments", org.token()), Map.of("method", "CASH", "amount", 600, "idempotencyKey", "rep-" + UUID.randomUUID()), 201);

        // A ward with 2 beds, one patient admitted then discharged home, another still in.
        String ward = create("/v1/inpatient/wards", org.token(), Map.of("facilityId", org.facilityId().toString(), "name", "Ward W", "bedLabels", List.of("1", "2"))).get("id").asText();
        JsonNode beds = fetch("/v1/inpatient/wards/" + ward + "/beds", org.token());
        UUID in1 = newPatient(org, "1970-01-01");
        UUID in2 = newPatient(org, "1971-01-01");
        JsonNode a1 = create("/v1/inpatient/admissions", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", in1.toString(), "bedId", beds.get(0).get("id").asText()));
        create("/v1/inpatient/admissions", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", in2.toString(), "bedId", beds.get(1).get("id").asText()));
        sendJson(post("/v1/inpatient/admissions/" + a1.get("id").asText() + "/discharge", doctor), Map.of("type", "LAMA", "summary", "Left against advice after counselling.", "noDiagnosisReason", "Left early"), 200);

        // One validated lab result.
        String test = create("/v1/lab/tests", org.token(), Map.of("code", "HB", "name", "Hb", "refLow", 12, "refHigh", 17)).get("id").asText();
        JsonNode lab = create("/v1/lab/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", adult.toString(), "testIds", List.of(test)));
        send(post("/v1/lab/orders/" + lab.get("id").asText() + "/collect", tech1), 200);
        String item = lab.get("items").get(0).get("id").asText();
        sendJson(post("/v1/lab/items/" + item + "/result", tech1), Map.of("numeric", 9), 200);
        send(post("/v1/lab/items/" + item + "/validate", tech2), 200);

        String q = "?facilityId=" + org.facilityId() + "&from=" + today.minusDays(1) + "&to=" + today.plusDays(1);
        JsonNode o = fetch("/v1/reports/overview" + q, org.token());
        JsonNode op = o.get("outpatient");
        // Three outpatient visits, three different people; one child under five.
        assertThat(op.get("visits").asInt()).isEqualTo(3);
        assertThat(op.get("uniquePatients").asInt()).isEqualTo(3);
        assertThat(op.get("under5").asInt()).isEqualTo(1);
        assertThat(op.get("fiveAndOver").asInt()).isEqualTo(2);
        assertThat(op.get("topDiagnoses").get(0).get("key").asText()).startsWith("CA40");
        assertThat(op.get("topDiagnoses").get(0).get("count").asInt()).isEqualTo(2);
        assertThat(op.get("note").asText()).contains("Not the official MOH 705 layout");
        JsonNode fin = o.get("finance");
        assertThat(fin.get("invoiced").decimalValue()).isEqualByComparingTo("1000");
        assertThat(fin.get("collected").decimalValue()).isEqualByComparingTo("600");
        assertThat(fin.get("outstanding").decimalValue()).isEqualByComparingTo("400");
        assertThat(fin.get("collectedByMethod").get(0).get("key").asText()).isEqualTo("CASH");
        JsonNode ip = o.get("inpatient");
        assertThat(ip.get("admissions").asInt()).isEqualTo(2);
        assertThat(ip.get("discharges").asInt()).isEqualTo(1);
        assertThat(ip.get("dischargesByOutcome").get(0).get("key").asText()).isEqualTo("LAMA");
        assertThat(ip.get("bedsTotal").asInt()).isEqualTo(2);
        assertThat(ip.get("bedsOccupied").asInt()).isEqualTo(1);
        assertThat(ip.get("occupancyPercent").asDouble()).isEqualTo(50.0);
        JsonNode lb = o.get("laboratory");
        assertThat(lb.get("tests").asInt()).isEqualTo(1);
        assertThat(lb.get("validated").asInt()).isEqualTo(1);
        assertThat(lb.get("byFlag").get(0).get("key").asText()).isEqualTo("L");
        // No patient is named anywhere in the output.
        assertThat(o.toString()).doesNotContain("Ort");
        // A range in the past has none of it.
        JsonNode empty = fetch("/v1/reports/outpatient?facilityId=" + org.facilityId() + "&from=2020-01-01&to=2020-01-31", org.token());
        assertThat(empty.get("visits").asInt()).isZero();
    }

    @Test
    void rangesAreBoundedAndReportsNeedTheRightAndTheFacility() throws Exception {
        Org a = newOrg("rep-a");
        Org b = newOrg("rep-b");
        String base = "/v1/reports/overview?facilityId=" + a.facilityId();
        send(get(base + "&from=2020-01-01&to=2021-12-31", a.token()), 400);
        send(get(base + "&from=2021-01-31&to=2021-01-01", a.token()), 400);
        send(get(base + "&from=2024-01-01&to=2024-01-31", userWithRole(a, "NURSE", "NURSE")), 403);
        send(get(base + "&from=2024-01-01&to=2024-01-31", userWithRole(a, "AUDITOR", "ACCOUNTANT")), 200);
        // Another organisation is told the facility is not theirs.
        send(get(base + "&from=2024-01-01&to=2024-01-31", b.token()), 403);
    }
}
