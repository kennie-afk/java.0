package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class BillingTest extends IntegrationTest {

    private UUID newPatient(Org org) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Bill", "Pay" + UUID.randomUUID().toString().substring(0, 6), "1979-09-09")).get("id").asText());
    }

    /** An issued invoice of exactly `total` shillings, as the cashier would produce it. */
    private String issued(Org org, String cashier, UUID patient, int total) throws Exception {
        JsonNode inv = create("/v1/billing/invoices", cashier, Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString()));
        String id = inv.get("id").asText();
        sendJson(post("/v1/billing/invoices/" + id + "/lines", cashier), Map.of("description", "Consultation", "unitPrice", total, "quantity", 1), 200);
        send(post("/v1/billing/invoices/" + id + "/issue", cashier), 200);
        return id;
    }

    @Test
    void anInvoiceIsBuiltFromThePriceListFrozenOnIssueAndPaidInInstalments() throws Exception {
        Org org = newOrg("bill");
        String cashier = userWithRole(org, "CASHIER", "ACCOUNTANT");
        UUID p = newPatient(org);
        create("/v1/billing/charges", org.token(), Map.of("code", "CONS", "name", "Consultation", "category", "CONSULTATION", "price", 800));
        sendJson(post("/v1/billing/charges", cashier), Map.of("code", "X", "name", "Cheap", "category", "OTHER", "price", 1), 403);
        String chargeId = fetch("/v1/billing/charges", cashier).get(0).get("id").asText();
        JsonNode inv = create("/v1/billing/invoices", cashier, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString()));
        String id = inv.get("id").asText();
        assertThat(inv.get("invoiceNumber").asText()).startsWith("INV-");
        sendJson(post("/v1/billing/invoices/" + id + "/lines", cashier), Map.of("chargeId", chargeId, "quantity", 1), 200);
        JsonNode withLine = sendJson(post("/v1/billing/invoices/" + id + "/lines", cashier), Map.of("description", "Dressing pack", "unitPrice", 250.50, "quantity", 2), 200);
        assertThat(withLine.get("total").decimalValue()).isEqualByComparingTo("1301.00");
        // An invoice cannot be paid before it is issued; an empty one cannot be issued.
        sendJson(post("/v1/billing/invoices/" + id + "/payments", cashier), Map.of("method", "CASH", "amount", 100, "idempotencyKey", "key-" + UUID.randomUUID()), 409);
        send(post("/v1/billing/invoices/" + id + "/issue", cashier), 200);
        // Frozen: neither the API nor the database allows editing the lines now.
        sendJson(post("/v1/billing/invoices/" + id + "/lines", cashier), Map.of("description", "Late addition", "unitPrice", 10, "quantity", 1), 409);
        assertThrows(Exception.class, () -> asOwner("UPDATE invoice_lines SET unit_price = 1 WHERE invoice_id = '" + id + "'"));
        assertThrows(Exception.class, () -> asOwner("DELETE FROM invoice_lines WHERE invoice_id = '" + id + "'"));

        String key = "key-" + UUID.randomUUID();
        JsonNode first = sendJson(post("/v1/billing/invoices/" + id + "/payments", cashier), Map.of("method", "CASH", "amount", 500, "idempotencyKey", key), 201);
        assertThat(first.get("receiptNumber").asText()).startsWith("RCT-");
        // A retry with the same key returns the same payment and does not charge twice.
        JsonNode retry = sendJson(post("/v1/billing/invoices/" + id + "/payments", cashier), Map.of("method", "CASH", "amount", 500, "idempotencyKey", key), 201);
        assertThat(retry.get("id").asText()).isEqualTo(first.get("id").asText());
        sendJson(post("/v1/billing/invoices/" + id + "/payments", cashier), Map.of("method", "CASH", "amount", 600, "idempotencyKey", key), 409);
        JsonNode mid = fetch("/v1/billing/invoices/" + id, cashier);
        assertThat(mid.get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(mid.get("balance").decimalValue()).isEqualByComparingTo("801.00");
        // Overpaying is refused; the exact balance settles it.
        sendJson(post("/v1/billing/invoices/" + id + "/payments", cashier), Map.of("method", "CARD", "amount", 801.01, "idempotencyKey", "key-" + UUID.randomUUID()), 409);
        sendJson(post("/v1/billing/invoices/" + id + "/payments", cashier), Map.of("method", "CARD", "amount", 801, "reference", "POS-4411", "idempotencyKey", "key-" + UUID.randomUUID()), 201);
        assertThat(fetch("/v1/billing/invoices/" + id, cashier).get("status").asText()).isEqualTo("PAID");
        sendJson(post("/v1/billing/invoices/" + id + "/payments", cashier), Map.of("method", "CASH", "amount", 1, "idempotencyKey", "key-" + UUID.randomUUID()), 409);
        // Only someone with the refund right may reverse; reversing reopens the balance.
        String payment = fetch("/v1/billing/invoices/" + id, cashier).get("payments").get(1).get("id").asText();
        sendJson(post("/v1/billing/payments/" + payment + "/reverse", cashier), Map.of("reason", "Card declined later"), 403);
        sendJson(post("/v1/billing/payments/" + payment + "/reverse", org.token()), Map.of("reason", "Card declined later"), 200);
        JsonNode after = fetch("/v1/billing/invoices/" + id, cashier);
        assertThat(after.get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(after.get("balance").decimalValue()).isEqualByComparingTo("801.00");
        assertThat(fetch("/v1/audit/verify", org.token()).findValuesAsText("intact")).containsOnly("true");
    }

    @Test
    void mpesaIsMockedByDefaultAndCompletionIsIdempotent() throws Exception {
        Org org = newOrg("mpesa");
        String cashier = userWithRole(org, "CASHIER", "ACCOUNTANT");
        UUID p = newPatient(org);
        String id = issued(org, cashier, p, 1000);
        sendJson(post("/v1/billing/invoices/" + id + "/mpesa", cashier), Map.of("phone", "12345", "amount", 400, "idempotencyKey", "stk-" + UUID.randomUUID()), 400);
        String key = "stk-" + UUID.randomUUID();
        JsonNode req = sendJson(post("/v1/billing/invoices/" + id + "/mpesa", cashier), Map.of("phone", "0712 345 678", "amount", 400, "idempotencyKey", key), 202);
        assertThat(req.get("note").asText()).contains("simulated");
        String checkout = req.get("payment").get("mpesaCheckoutId").asText();
        assertThat(checkout).startsWith("MOCK-");
        assertThat(req.get("payment").get("status").asText()).isEqualTo("PENDING");
        assertThat(req.get("payment").get("mpesaPhone").asText()).isEqualTo("+254712345678");
        // A repeated request with the same key does not push a second prompt.
        assertThat(sendJson(post("/v1/billing/invoices/" + id + "/mpesa", cashier), Map.of("phone", "0712345678", "amount", 400, "idempotencyKey", key), 202)
                .get("payment").get("mpesaCheckoutId").asText()).isEqualTo(checkout);
        // Pending money does not count as paid.
        assertThat(fetch("/v1/billing/invoices/" + id, cashier).get("amountPaid").decimalValue()).isEqualByComparingTo("0");
        JsonNode done = sendJson(post("/v1/billing/mpesa/mock/complete", cashier), Map.of("checkoutRequestId", checkout, "success", true, "receiptNumber", "QWE123RTY4"), 200);
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("receiptNumber").asText()).startsWith("RCT-");
        // The callback arriving twice must not pay twice.
        sendJson(post("/v1/billing/mpesa/mock/complete", cashier), Map.of("checkoutRequestId", checkout, "success", true, "receiptNumber", "QWE123RTY4"), 200);
        assertThat(fetch("/v1/billing/invoices/" + id, cashier).get("amountPaid").decimalValue()).isEqualByComparingTo("400");
        // A cancelled prompt fails the payment and leaves the balance alone.
        JsonNode second = sendJson(post("/v1/billing/invoices/" + id + "/mpesa", cashier), Map.of("phone", "0712345678", "amount", 600, "idempotencyKey", "stk-" + UUID.randomUUID()), 202);
        JsonNode failed = sendJson(post("/v1/billing/mpesa/mock/complete", cashier), Map.of("checkoutRequestId", second.get("payment").get("mpesaCheckoutId").asText(), "success", false), 200);
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(fetch("/v1/billing/invoices/" + id, cashier).get("balance").decimalValue()).isEqualByComparingTo("600");
        // The same M-Pesa receipt cannot settle two payments.
        JsonNode third = sendJson(post("/v1/billing/invoices/" + id + "/mpesa", cashier), Map.of("phone", "0712345678", "amount", 600, "idempotencyKey", "stk-" + UUID.randomUUID()), 202);
        sendJson(post("/v1/billing/mpesa/mock/complete", cashier), Map.of("checkoutRequestId", third.get("payment").get("mpesaCheckoutId").asText(), "success", true, "receiptNumber", "QWE123RTY4"), 409);
        sendJson(post("/v1/billing/mpesa/mock/complete", cashier), Map.of("checkoutRequestId", "MOCK-DOESNOTEXIST", "success", true), 404);
    }

    @Test
    void parallelPaymentsCannotTakeMoreThanTheBalance() throws Exception {
        Org org = newOrg("parallel");
        String cashier = userWithRole(org, "CASHIER", "ACCOUNTANT");
        String id = issued(org, cashier, newPatient(org), 100);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String key = "par-" + i + "-" + UUID.randomUUID();
            futures.add(pool.submit(() -> mvc.perform(post("/v1/billing/invoices/" + id + "/payments", cashier).content(json.writeValueAsString(Map.of("method", "CASH", "amount", 40, "idempotencyKey", key))))
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
        assertThat(fetch("/v1/billing/invoices/" + id, cashier).get("amountPaid").decimalValue()).isEqualByComparingTo("80");
    }

    @Test
    void anEncountersDispensingsAndLabsAreBilledExactlyOnce() throws Exception {
        Org org = newOrg("import");
        String doctor = userWithRole(org, "DOCTOR");
        String pharmacist = userWithRole(org, "PHARMACIST", "PHARMACIST");
        String tech1 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String cashier = userWithRole(org, "CASHIER", "ACCOUNTANT");
        UUID p = newPatient(org);
        String enc = create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "type", "OPD")).get("id").asText();
        String drug = create("/v1/pharmacy/drugs", org.token(), Map.of("genericName", "Cotrimoxazole", "form", "Tablet", "unitPrice", 12.5)).get("id").asText();
        create("/v1/pharmacy/stock/receipts", pharmacist, Map.of("facilityId", org.facilityId().toString(), "drugId", drug, "batchNo", "C1", "expiryDate", LocalDate.now().plusYears(1).toString(), "quantity", 100));
        String order = create("/v1/clinical/encounters/" + enc + "/orders", doctor, Map.of("kind", "MEDICATION", "description", "Cotrimoxazole", "drugId", drug, "drugName", "Cotrimoxazole", "quantity", 10)).get("id").asText();
        create("/v1/pharmacy/dispense", pharmacist, Map.of("orderId", order, "quantity", 10));
        String test = create("/v1/lab/tests", org.token(), Map.of("code", "MP", "name", "Malaria slide", "resultType", "TEXT", "price", 300)).get("id").asText();
        JsonNode lab = create("/v1/lab/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "encounterId", enc, "testIds", List.of(test)));
        send(post("/v1/lab/orders/" + lab.get("id").asText() + "/collect", tech1), 200);
        sendJson(post("/v1/lab/items/" + lab.get("items").get(0).get("id").asText() + "/result", tech1), Map.of("text", "No parasites seen"), 200);

        JsonNode inv = create("/v1/billing/invoices", cashier, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "encounterId", enc));
        JsonNode imported = send(post("/v1/billing/invoices/" + inv.get("id").asText() + "/import-encounter", cashier), 200);
        assertThat(imported.get("lines")).hasSize(2);
        assertThat(imported.get("total").decimalValue()).isEqualByComparingTo("425.00");
        // Running it again adds nothing; a second invoice for the same encounter cannot bill them again.
        assertThat(send(post("/v1/billing/invoices/" + inv.get("id").asText() + "/import-encounter", cashier), 200).get("lines")).hasSize(2);
        JsonNode second = create("/v1/billing/invoices", cashier, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "encounterId", enc));
        assertThat(send(post("/v1/billing/invoices/" + second.get("id").asText() + "/import-encounter", cashier), 200).get("lines")).isEmpty();
        // Voiding the first (it has no payments) releases the items for the second.
        sendJson(post("/v1/billing/invoices/" + inv.get("id").asText() + "/void", org.token()), Map.of("reason", "Billed to the wrong payer"), 200);
        assertThat(send(post("/v1/billing/invoices/" + second.get("id").asText() + "/import-encounter", cashier), 200).get("lines")).hasSize(2);
    }

    @Test
    void invoicesAndPaymentsBelongToOneOrganisation() throws Exception {
        Org a = newOrg("bill-a");
        Org b = newOrg("bill-b");
        String id = issued(a, a.token(), newPatient(a), 500);
        send(get("/v1/billing/invoices/" + id, b.token()), 404);
        sendJson(post("/v1/billing/invoices/" + id + "/payments", b.token()), Map.of("method", "CASH", "amount", 500, "idempotencyKey", "other-" + UUID.randomUUID()), 404);
        assertThat(fetch("/v1/billing/invoices", b.token()).get("data")).isEmpty();
        // A payer type outside the list is refused, and a voided invoice cannot be paid.
        sendJson(post("/v1/billing/invoices", a.token()), Map.of("facilityId", a.facilityId().toString(), "patientId", newPatient(a).toString(), "payerType", "BARTER"), 400);
        sendJson(post("/v1/billing/invoices/" + id + "/void", a.token()), Map.of("reason", "Raised in error"), 200);
        sendJson(post("/v1/billing/invoices/" + id + "/payments", a.token()), Map.of("method", "CASH", "amount", 500, "idempotencyKey", "late-" + UUID.randomUUID()), 409);
    }
}
