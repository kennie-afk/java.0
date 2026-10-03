package com.hms.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationOutboxTest extends IntegrationTest {

    @Autowired NotificationDispatcher dispatcher;
    @Autowired List<NotificationProvider> providers;

    private static String slug(Org org) {
        return org.email().replace("@example.org", "");
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID patientWithContact(Org org, String phone, String email) throws Exception {
        Object[] extra = phone == null && email == null ? new Object[0]
                : phone == null ? new Object[] {"email", email} : email == null ? new Object[] {"phone", phone} : new Object[] {"phone", phone, "email", email};
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Wanjiru", "Notice" + unique(), "1990-04-04", extra)).get("id").asText());
    }

    /** The registry stores Kenyan mobiles as +254..., so that is what the outbox holds. */
    private static String canonical(String local) {
        return "+254" + local.substring(1);
    }

    private List<NotificationProvider.Outbound> sent(String channel) {
        return providers.stream().filter(p -> p.channel().equals(channel)).map(p -> ((NotificationProviders.MockProvider) p).recent()).findFirst().orElseThrow();
    }

    /** The test database keeps rows from earlier runs, so a single batch may not reach this test's messages: run passes until none are due. */
    private void drain() {
        while (dispatcher.dispatchOnce() > 0) {
            // keep going
        }
    }

    private List<Map<String, Object>> owner(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, OWNER, OWNER_PASSWORD); Statement s = c.createStatement()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            s.execute(sql);
            ResultSet rs = s.getResultSet();
            while (rs != null && rs.next()) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    @Test
    void anInvitationQueuesANoticeThatNeverCarriesTheCode() throws Exception {
        drain();
        Org org = newOrg("outbox");
        String phone = "0722" + (int) (Math.random() * 900000 + 100000);
        String email = "patient" + unique() + "@example.test";
        UUID patient = patientWithContact(org, phone, email);
        String sentToPhone = canonical(phone);

        JsonNode inv = create("/v1/portal/invitations", org.token(), Map.of("patientId", patient.toString()));
        String code = inv.get("code").asText();
        assertThat(inv.get("notices")).hasSize(2);
        assertThat(inv.get("notices").toString()).contains("SMS to ").contains("EMAIL to ").doesNotContain(phone).doesNotContain(email);

        // Queued but not yet sent: delivery happens after the transaction, never inside it.
        JsonNode before = fetch("/v1/portal/accounts/" + patient + "/notifications", org.token());
        assertThat(before).hasSize(2);
        assertThat(before.get(0).get("status").asText()).isEqualTo("PENDING");

        drain();
        JsonNode after = fetch("/v1/portal/accounts/" + patient + "/notifications", org.token());
        assertThat(after.get(0).get("status").asText()).isEqualTo("SENT");
        assertThat(after.get(1).get("status").asText()).isEqualTo("SENT");
        assertThat(after.toString()).doesNotContain(phone).doesNotContain(email);

        var sms = sent("SMS").stream().filter(m -> m.recipient().equals(sentToPhone)).toList();
        var mail = sent("EMAIL").stream().filter(m -> m.recipient().equals(email)).toList();
        assertThat(sms).hasSize(1);
        assertThat(mail).hasSize(1);
        assertThat(sms.get(0).body()).contains("one-time code in person").contains("Wanjiru");
        // The secret stays out of every message, in any spelling.
        String plain = code.replace("-", "");
        assertThat(sms.get(0).body() + mail.get(0).body() + mail.get(0).subject()).doesNotContain(code).doesNotContain(plain);
        // A second pass finds nothing left to do: a message is delivered once.
        drain();
        assertThat(sent("SMS").stream().filter(m -> m.recipient().equals(sentToPhone))).hasSize(1);
    }

    @Test
    void creatingTheAccountTellsThePatient() throws Exception {
        drain();
        Org org = newOrg("welcome");
        String phone = "0733" + (int) (Math.random() * 900000 + 100000);
        UUID patient = patientWithContact(org, phone, null);
        String sentTo = canonical(phone);
        String code = create("/v1/portal/invitations", org.token(), Map.of("patientId", patient.toString())).get("code").asText();
        drain();
        sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", code, "birthDate", "1990-04-04", "login", "w" + unique() + "@example.test", "password", "a-long-password-1"), 200);

        JsonNode list = fetch("/v1/portal/accounts/" + patient + "/notifications", org.token());
        assertThat(list).hasSize(2);
        assertThat(list.toString()).contains("PORTAL_ACTIVATED").contains("PORTAL_INVITED");
        drain();
        var welcome = sent("SMS").stream().filter(m -> m.recipient().equals(sentTo) && m.body().contains("was just created")).toList();
        assertThat(welcome).hasSize(1);
        assertThat(welcome.get(0).body()).contains("If this was not you");
    }

    @Test
    void aPatientWithNoContactGetsNothingAndTheInvitationStillWorks() throws Exception {
        Org org = newOrg("nocontact");
        UUID patient = patientWithContact(org, null, null);
        JsonNode inv = create("/v1/portal/invitations", org.token(), Map.of("patientId", patient.toString()));
        assertThat(inv.get("code").asText()).isNotBlank();
        assertThat(inv.get("notices")).isEmpty();
        assertThat(fetch("/v1/portal/accounts/" + patient + "/notifications", org.token())).isEmpty();
    }

    @Test
    void anotherOrganisationCannotSeeTheMessages() throws Exception {
        Org a = newOrg("outa");
        Org b = newOrg("outb");
        UUID patient = patientWithContact(a, "0744" + (int) (Math.random() * 900000 + 100000), null);
        create("/v1/portal/invitations", a.token(), Map.of("patientId", patient.toString()));
        send(get("/v1/portal/accounts/" + patient + "/notifications", b.token()), 404);
        // Staff without the portal permission are refused.
        send(get("/v1/portal/accounts/" + patient + "/notifications", userWithRole(a, "PHARMACIST", "PHARMACIST")), 403);
    }

    @Test
    void failuresRetryWithBackoffAndPermanentOnesStopAtOnce() throws Exception {
        Org org = newOrg("retry");
        UUID patient = patientWithContact(org, null, null);
        String id1 = insertRow(org, patient);
        String id2 = insertRow(org, patient);

        // A leased row is not claimed again until its lease runs out.
        assertThat(owner("SELECT id FROM outbox_claim(1000, 3600) WHERE id IN ('" + id1 + "', '" + id2 + "')")).hasSize(2);
        assertThat(owner("SELECT id FROM outbox_claim(1000, 3600) WHERE id IN ('" + id1 + "', '" + id2 + "')")).isEmpty();

        // Temporary failure: back to PENDING with a later attempt, until the attempts run out.
        owner("SELECT outbox_finish('" + id1 + "', false, false, 'timeout', 3, 0)");
        assertThat(state(id1)).containsEntry("status", "PENDING").containsEntry("attempts", 1);
        owner("UPDATE notification_outbox SET next_attempt_at = now() WHERE id = '" + id1 + "'");
        owner("SELECT * FROM outbox_claim(1000, 0) WHERE id = '" + id1 + "'");
        owner("SELECT outbox_finish('" + id1 + "', false, false, 'timeout', 3, 0)");
        owner("SELECT * FROM outbox_claim(1000, 0) WHERE id = '" + id1 + "'");
        owner("SELECT outbox_finish('" + id1 + "', false, false, 'timeout', 3, 0)");
        assertThat(state(id1)).containsEntry("status", "FAILED").containsEntry("attempts", 3).containsEntry("last_error", "timeout");

        // Permanent failure: no retry at all.
        owner("SELECT outbox_finish('" + id2 + "', false, true, 'not configured', 5, 60)");
        assertThat(state(id2)).containsEntry("status", "FAILED").containsEntry("attempts", 1);
        // A finished row cannot be finished again.
        owner("SELECT outbox_finish('" + id2 + "', true, false, null, 5, 60)");
        assertThat(state(id2)).containsEntry("status", "FAILED");
    }

    private String insertRow(Org org, UUID patient) throws Exception {
        return String.valueOf(owner("INSERT INTO notification_outbox (org_id, patient_id, channel, recipient, template, params) VALUES ('" + org.orgId() + "', '" + patient
                + "', 'SMS', '0700000000', 'PORTAL_ACTIVATED', '{}') RETURNING id").get(0).get("id"));
    }

    private Map<String, Object> state(String id) throws Exception {
        return owner("SELECT status, attempts, last_error FROM notification_outbox WHERE id = '" + id + "'").get(0);
    }

    @Test
    void liveModeFailsLoudlyInsteadOfPretending() {
        var live = new NotificationProviders.UnconfiguredProvider("SMS");
        var ex = org.junit.jupiter.api.Assertions.assertThrows(NotificationProvider.DeliveryException.class,
                () -> live.send(new NotificationProvider.Outbound(UUID.randomUUID(), "SMS", "0700000000", "s", "b")));
        assertThat(ex.permanent()).isTrue();
        assertThat(ex.getMessage()).contains("not implemented");
        assertThat(live.isMock()).isFalse();
    }

    @Test
    void recipientsAreMaskedInLogsAndListings() {
        assertThat(NotificationProviders.mask("0722123456")).isEqualTo("0722•••456");
        assertThat(NotificationProviders.mask("jane.doe@example.org")).isEqualTo("j***@example.org");
        assertThat(NotificationProviders.mask("ab")).isEqualTo("***");
    }
}
