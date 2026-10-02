package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class PharmacyTest extends IntegrationTest {

    private String drug(Org org, String name, boolean controlled) throws Exception {
        return create("/v1/pharmacy/drugs", org.token(), Map.of("genericName", name, "strength", "500mg", "form", "Tablet", "unit", "tablet", "controlled", controlled,
                "unitPrice", 5, "reorderLevel", 20)).get("id").asText();
    }

    private JsonNode receive(Org org, String token, String drugId, String batch, LocalDate expiry, int qty) throws Exception {
        return create("/v1/pharmacy/stock/receipts", token, Map.of("facilityId", org.facilityId().toString(), "drugId", drugId, "batchNo", batch,
                "expiryDate", expiry.toString(), "quantity", qty, "unitCost", 2));
    }

    private UUID newPatient(Org org, String given) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), given, "Pharm" + UUID.randomUUID().toString().substring(0, 6), "1991-05-05")).get("id").asText());
    }

    private String encounter(Org org, String doctor, UUID patient) throws Exception {
        return create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString(), "type", "OPD")).get("id").asText();
    }

    private String rx(String doctor, String encounter, String drugId, String name, int qty) throws Exception {
        return create("/v1/clinical/encounters/" + encounter + "/orders", doctor, Map.of("kind", "MEDICATION", "description", name, "drugId", drugId, "drugName", name,
                "dose", "1 tablet", "frequency", "BD", "durationDays", 7, "quantity", qty)).get("id").asText();
    }

    @Test
    void queueWithoutFacilityIdIsABadRequestNotAServerError() throws Exception {
        Org org = newOrg("queue400");
        String pharmacist = userWithRole(org, "PHARMACIST", "PHARMACIST");
        JsonNode problem = send(get("/v1/pharmacy/queue", pharmacist), 400);
        assertThat(problem.get("code").asText()).isEqualTo("malformed_request");
    }

    @Test
    void dispensingDrawsFromTheEarliestExpiryFirstAndSpansBatches() throws Exception {
        Org org = newOrg("fefo");
        String pharmacist = userWithRole(org, "PHARMACIST", "PHARMACIST");
        String doctor = userWithRole(org, "DOCTOR");
        String drug = drug(org, "Metformin", false);
        receive(org, pharmacist, drug, "LATE", LocalDate.now().plusMonths(18), 50);
        receive(org, pharmacist, drug, "SOON", LocalDate.now().plusMonths(3), 10);
        UUID p = newPatient(org, "Chebet");
        String enc = encounter(org, doctor, p);
        String order = rx(doctor, enc, drug, "Metformin", 25);
        // The queue shows it waiting.
        assertThat(fetch("/v1/pharmacy/queue?facilityId=" + org.facilityId(), pharmacist).get("data")).hasSize(1);
        JsonNode first = create("/v1/pharmacy/dispense", pharmacist, Map.of("orderId", order, "quantity", 15));
        assertThat(first.get("orderStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(first.get("sources")).hasSize(2);
        assertThat(first.get("sources").get(0).get("batchNo").asText()).isEqualTo("SOON");
        assertThat(first.get("sources").get(0).get("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(first.get("sources").get(1).get("batchNo").asText()).isEqualTo("LATE");
        // More than remains on the order is refused; the rest completes it.
        sendJson(post("/v1/pharmacy/dispense", pharmacist), Map.of("orderId", order, "quantity", 11), 409);
        JsonNode second = create("/v1/pharmacy/dispense", pharmacist, Map.of("orderId", order, "quantity", 10));
        assertThat(second.get("orderStatus").asText()).isEqualTo("COMPLETED");
        sendJson(post("/v1/pharmacy/dispense", pharmacist), Map.of("orderId", order, "quantity", 1), 409);
        JsonNode stock = fetch("/v1/pharmacy/stock?facilityId=" + org.facilityId() + "&q=metformin", pharmacist).get("data").get(0);
        assertThat(stock.get("usable").decimalValue()).isEqualByComparingTo("35");
        // The ledger holds the receipts and the dispensings, newest first.
        JsonNode moves = fetch("/v1/pharmacy/stock/movements?facilityId=" + org.facilityId() + "&drugId=" + drug, pharmacist).get("data");
        assertThat(moves.findValuesAsText("reason")).contains("RECEIPT", "DISPENSE");
        assertThat(fetch("/v1/pharmacy/queue?facilityId=" + org.facilityId(), pharmacist).get("data")).isEmpty();
        assertThat(fetch("/v1/clinical/encounters/" + enc, doctor).get("orders").get(0).get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void expiredStockIsNeverDispensedOrReceivedAndShortagesAreReportedHonestly() throws Exception {
        Org org = newOrg("expiry");
        String pharmacist = userWithRole(org, "PHARMACIST", "PHARMACIST");
        String doctor = userWithRole(org, "DOCTOR");
        String drug = drug(org, "Amlodipine", false);
        sendJson(post("/v1/pharmacy/stock/receipts", pharmacist), Map.of("facilityId", org.facilityId().toString(), "drugId", drug, "batchNo", "OLD",
                "expiryDate", LocalDate.now().minusDays(1).toString(), "quantity", 10), 400);
        receive(org, pharmacist, drug, "OK", LocalDate.now().plusMonths(6), 5);
        // A batch that went out of date on the shelf: put it in as the owner, bypassing the receipt rule.
        asOwner("INSERT INTO stock_batches (org_id, facility_id, drug_id, batch_no, expiry_date, quantity) VALUES ('" + org.orgId() + "', '" + org.facilityId() + "', '" + drug + "', 'EXPIRED', current_date - 5, 100)");
        UUID p = newPatient(org, "Wafula");
        String enc = encounter(org, doctor, p);
        String order = rx(doctor, enc, drug, "Amlodipine", 20);
        JsonNode short1 = sendJson(post("/v1/pharmacy/dispense", pharmacist), Map.of("orderId", order, "quantity", 20), 409);
        assertThat(short1.get("code").asText()).isEqualTo("insufficient_stock");
        assertThat(short1.get("detail").asText()).contains("Only 5");
        JsonNode line = fetch("/v1/pharmacy/stock?facilityId=" + org.facilityId() + "&q=amlodipine", pharmacist).get("data").get(0);
        assertThat(line.get("usable").decimalValue()).isEqualByComparingTo("5");
        assertThat(line.get("expired").decimalValue()).isEqualByComparingTo("100");
        assertThat(line.get("belowReorder").asBoolean()).isTrue();
        // Writing the expired stock off leaves a ledger entry and needs a reason.
        String expiredBatch = fetch("/v1/pharmacy/stock/batches?facilityId=" + org.facilityId() + "&drugId=" + drug, pharmacist).findValuesAsText("id").stream()
                .filter(id -> true).skip(0).findFirst().get();
        JsonNode batches = fetch("/v1/pharmacy/stock/batches?facilityId=" + org.facilityId() + "&drugId=" + drug, pharmacist);
        String expiredId = null;
        for (JsonNode b : batches) {
            if (b.get("expired").asBoolean()) {
                expiredId = b.get("id").asText();
            }
        }
        assertThat(expiredBatch).isNotBlank();
        sendJson(post("/v1/pharmacy/stock/adjustments", pharmacist), Map.of("batchId", expiredId, "delta", -100, "reason", "WRITE_OFF", "note", "x"), 400);
        sendJson(post("/v1/pharmacy/stock/adjustments", pharmacist), Map.of("batchId", expiredId, "delta", -100, "reason", "WRITE_OFF", "note", "Expired on shelf, destroyed"), 200);
        sendJson(post("/v1/pharmacy/stock/adjustments", pharmacist), Map.of("batchId", expiredId, "delta", -1, "reason", "ADJUSTMENT", "note", "below zero attempt"), 409);
    }

    @Test
    void concurrentDispensingNeverOversellsStock() throws Exception {
        Org org = newOrg("oversell");
        String pharmacist = userWithRole(org, "PHARMACIST", "PHARMACIST");
        String doctor = userWithRole(org, "DOCTOR");
        String drug = drug(org, "Ibuprofen", false);
        receive(org, pharmacist, drug, "B1", LocalDate.now().plusMonths(12), 10);
        List<String> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID p = newPatient(org, "Buyer" + (char) ('A' + i));
            orders.add(rx(doctor, encounter(org, doctor, p), drug, "Ibuprofen", 4));
        }
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futures = new ArrayList<>();
        for (String order : orders) {
            futures.add(pool.submit(() -> mvc.perform(post("/v1/pharmacy/dispense", pharmacist).content(json.writeValueAsString(Map.of("orderId", order, "quantity", 4))))
                    .andReturn().getResponse().getStatus()));
        }
        int ok = 0;
        for (Future<Integer> f : futures) {
            if (f.get() == 201) {
                ok++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(2);
        JsonNode stock = fetch("/v1/pharmacy/stock?facilityId=" + org.facilityId() + "&q=ibuprofen", pharmacist).get("data").get(0);
        assertThat(stock.get("usable").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(10 - 4 * ok));
    }

    @Test
    void aControlledDrugNeedsADifferentWitnessAndIsInTheRegister() throws Exception {
        Org org = newOrg("controlled");
        String pharmacist = userWithRole(org, "PHARMACIST", "PHARMACIST");
        String witness = userWithRole(org, "NURSE", "NURSE");
        String doctor = userWithRole(org, "DOCTOR");
        String drug = drug(org, "Morphine", true);
        receive(org, pharmacist, drug, "CD1", LocalDate.now().plusMonths(12), 20);
        UUID p = newPatient(org, "Pain");
        String order = rx(doctor, encounter(org, doctor, p), drug, "Morphine", 5);
        sendJson(post("/v1/pharmacy/dispense", pharmacist), Map.of("orderId", order, "quantity", 5), 400);
        UUID self = practitionerIdOf(pharmacist);
        sendJson(post("/v1/pharmacy/dispense", pharmacist), Map.of("orderId", order, "quantity", 5, "witnessId", self.toString()), 400);
        sendJson(post("/v1/pharmacy/dispense", pharmacist), Map.of("orderId", order, "quantity", 5, "witnessId", UUID.randomUUID().toString()), 400);
        JsonNode done = create("/v1/pharmacy/dispense", pharmacist, Map.of("orderId", order, "quantity", 5, "witnessId", practitionerIdOf(witness).toString()));
        assertThat(done.get("witnessId").asText()).isEqualTo(practitionerIdOf(witness).toString());
        JsonNode register = fetch("/v1/pharmacy/stock/movements?facilityId=" + org.facilityId() + "&controlledOnly=true", pharmacist).get("data");
        assertThat(register).hasSize(2);
        assertThat(register.get(0).get("witnessId").asText()).isEqualTo(practitionerIdOf(witness).toString());
        // The ledger cannot be rewritten, even by the owner.
        assertThrows(Exception.class, () -> asOwner("DELETE FROM stock_movements WHERE org_id = '" + org.orgId() + "'"));
        // Changing controlled status with stock on hand is refused.
        sendJson(put("/v1/pharmacy/drugs/" + drug, org.token()), Map.of("genericName", "Morphine", "strength", "500mg", "form", "Tablet", "controlled", false), 409);
    }

    @Test
    void thePharmacistIsStoppedAtTheCounterWhenTheProductHandedOverMatchesAnAllergy() throws Exception {
        Org org = newOrg("rxallergy");
        String pharmacist = userWithRole(org, "PHARMACIST", "PHARMACIST");
        String doctor = userWithRole(org, "DOCTOR");
        // The prescriber typed a brand name the allergy check could not match; the formulary product is the generic.
        String drug = drug(org, "Penicillin", false);
        receive(org, pharmacist, drug, "P1", LocalDate.now().plusMonths(12), 30);
        UUID p = newPatient(org, "Allergic");
        String enc = encounter(org, doctor, p);
        String order = create("/v1/clinical/encounters/" + enc + "/orders", doctor, Map.of("kind", "MEDICATION", "description", "Brand X 500", "drugName", "Brand X 500",
                "quantity", 10)).get("id").asText();
        create("/v1/clinical/patients/" + p + "/allergies", doctor, Map.of("substance", "Penicillin", "severity", "SEVERE", "reaction", "Rash"));
        JsonNode blocked = sendJson(post("/v1/pharmacy/dispense", pharmacist), Map.of("orderId", order, "quantity", 10, "drugId", drug), 409);
        assertThat(blocked.get("code").asText()).isEqualTo("allergy_conflict");
        create("/v1/pharmacy/dispense", pharmacist, Map.of("orderId", order, "quantity", 10, "drugId", drug, "allergyOverrideReason", "Prescriber confirmed, supervised first dose"));
    }

    @Test
    void theFormularyAndStockBelongToOneOrganisationAndNurseCannotManageStock() throws Exception {
        Org a = newOrg("ph-a");
        Org b = newOrg("ph-b");
        String drugA = drug(a, "Zinc", false);
        assertThat(fetch("/v1/pharmacy/drugs", b.token()).get("data")).isEmpty();
        sendJson(post("/v1/pharmacy/stock/receipts", b.token()), Map.of("facilityId", b.facilityId().toString(), "drugId", drugA, "batchNo", "X", "expiryDate",
                LocalDate.now().plusYears(1).toString(), "quantity", 5), 404);
        sendJson(post("/v1/pharmacy/drugs", userWithRole(a, "NURSE", "NURSE")), Map.of("genericName", "Sneaky", "form", "Tablet"), 403);
        sendJson(post("/v1/pharmacy/drugs", a.token()), Map.of("genericName", "Zinc", "strength", "500mg", "form", "tablet"), 409);
    }
}
