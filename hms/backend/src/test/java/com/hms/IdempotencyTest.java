package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class IdempotencyTest extends IntegrationTest {

    private String encounter(Org org, String doctor, UUID patient) throws Exception {
        return create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString(), "type", "OPD")).get("id").asText();
    }

    private UUID newPatient(Org org) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Idem", "Potent" + UUID.randomUUID().toString().substring(0, 5), "1980-01-01")).get("id").asText());
    }

    private String key() {
        return "key-" + UUID.randomUUID();
    }

    private int vitalsCount(String enc, String token) throws Exception {
        return fetch("/v1/clinical/encounters/" + enc, token).get("vitals").size();
    }

    @Test
    void aWriteSentTwiceWithTheSameKeyIsCarriedOutOnceAndAnsweredTheSame() throws Exception {
        Org org = newOrg("idem");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        String enc = encounter(org, userWithRole(org, "DOCTOR"), newPatient(org));
        String k = key();
        String url = "/v1/clinical/encounters/" + enc + "/vitals";
        String body = json.writeValueAsString(Map.of("pulse", 72, "tempC", 36.8));
        var first = mvc.perform(post(url, nurse).header("Idempotency-Key", k).content(body)).andExpect(status().isCreated()).andReturn().getResponse();
        var second = mvc.perform(post(url, nurse).header("Idempotency-Key", k).content(body)).andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replay", "true")).andReturn().getResponse();
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(vitalsCount(enc, nurse)).isEqualTo(1);
        // Without a key, a repeat is a second recording.
        mvc.perform(post(url, nurse).content(body)).andExpect(status().isCreated());
        assertThat(vitalsCount(enc, nurse)).isEqualTo(2);
        // The same key with a different body is refused, and nothing is recorded.
        mvc.perform(post(url, nurse).header("Idempotency-Key", k).content(json.writeValueAsString(Map.of("pulse", 99)))).andExpect(status().isUnprocessableEntity());
        assertThat(vitalsCount(enc, nurse)).isEqualTo(2);
        // A malformed key is a client error.
        mvc.perform(post(url, nurse).header("Idempotency-Key", "short").content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void keysBelongToTheSignedInPersonAndAFailureIsRepeatedNotRetried() throws Exception {
        Org org = newOrg("idemown");
        String nurse1 = userWithRole(org, "NURSE", "NURSE");
        String nurse2 = userWithRole(org, "NURSE", "NURSE");
        String enc = encounter(org, userWithRole(org, "DOCTOR"), newPatient(org));
        String url = "/v1/clinical/encounters/" + enc + "/vitals";
        String body = json.writeValueAsString(Map.of("pulse", 70));
        String k = key();
        mvc.perform(post(url, nurse1).header("Idempotency-Key", k).content(body)).andExpect(status().isCreated());
        // Someone else using the same string is a different request, not a replay.
        mvc.perform(post(url, nurse2).header("Idempotency-Key", k).content(body)).andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replay"));
        assertThat(vitalsCount(enc, nurse1)).isEqualTo(2);
        // A refused write (invalid reading) keeps answering the same refusal for that key and stores nothing.
        String bad = key();
        String badBody = json.writeValueAsString(Map.of("tempC", 60));
        var a = mvc.perform(post(url, nurse1).header("Idempotency-Key", bad).content(badBody)).andExpect(status().isBadRequest()).andReturn().getResponse();
        var b = mvc.perform(post(url, nurse1).header("Idempotency-Key", bad).content(badBody)).andExpect(status().isBadRequest()).andExpect(header().string("Idempotent-Replay", "true")).andReturn().getResponse();
        assertThat(b.getContentAsString()).isEqualTo(a.getContentAsString());
        assertThat(vitalsCount(enc, nurse1)).isEqualTo(2);
        // A forbidden attempt is not remembered: once the person is allowed, the same key goes through.
        String lab = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String forbiddenKey = key();
        mvc.perform(post(url, lab).header("Idempotency-Key", forbiddenKey).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(url, lab).header("Idempotency-Key", forbiddenKey).content(body)).andExpect(status().isForbidden()).andExpect(header().doesNotExist("Idempotent-Replay"));
    }

    @Test
    void sameKeySentConcurrentlyRecordsOnce() throws Exception {
        Org org = newOrg("idemrace");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        String enc = encounter(org, userWithRole(org, "DOCTOR"), newPatient(org));
        String url = "/v1/clinical/encounters/" + enc + "/vitals";
        String body = json.writeValueAsString(Map.of("pulse", 66));
        String k = key();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Callable<Integer> c = () -> mvc.perform(post(url, nurse).header("Idempotency-Key", k).content(body)).andReturn().getResponse().getStatus();
            results.add(pool.submit(c));
        }
        int created = 0;
        for (Future<Integer> f : results) {
            int status = f.get();
            assertThat(status).isIn(201, 409);
            if (status == 201) {
                created++;
            }
        }
        pool.shutdown();
        assertThat(created).isGreaterThanOrEqualTo(1);
        assertThat(vitalsCount(enc, nurse)).isEqualTo(1);
    }
}
