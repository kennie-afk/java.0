package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class SchedulingTest extends IntegrationTest {

    private UUID patientId(Org org, String given) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), given, "Tester" + UUID.randomUUID().toString().substring(0, 6), "1990-01-01")).get("id").asText());
    }

    /** A clinic open every day, all day, run by the org's administrator. */
    private UUID clinic(Org org, int slotMinutes) throws Exception {
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            sessions.add(Map.of("practitionerId", org.adminId().toString(), "weekday", d, "startTime", "00:00:00", "endTime", "23:59:00"));
        }
        return UUID.fromString(create("/v1/scheduling/clinics", org.token(), Map.of("facilityId", org.facilityId().toString(), "name", "OPD " + UUID.randomUUID().toString().substring(0, 4),
                "specialty", "General", "slotMinutes", slotMinutes, "sessions", sessions)).get("id").asText());
    }

    private String tomorrow() {
        return LocalDate.now(ZoneId.of("Africa/Nairobi")).plusDays(1).toString();
    }

    @Test
    void aBookedSlotDisappearsFromAvailabilityAndFreesUpWhenCancelled() throws Exception {
        Org org = newOrg("sched");
        UUID clinic = clinic(org, 30);
        UUID patient = patientId(org, "Achieng");
        JsonNode slots = fetch("/v1/scheduling/slots?clinicId=" + clinic + "&date=" + tomorrow(), org.token());
        assertThat(slots.size()).isGreaterThan(40);
        String start = slots.get(5).get("start").asText();
        JsonNode appt = create("/v1/scheduling/appointments", org.token(), Map.of("clinicId", clinic.toString(), "patientId", patient.toString(),
                "practitionerId", org.adminId().toString(), "startsAt", start, "reason", "Follow-up"));
        assertThat(appt.get("status").asText()).isEqualTo("BOOKED");
        assertThat(fetch("/v1/scheduling/slots?clinicId=" + clinic + "&date=" + tomorrow(), org.token()).findValuesAsText("start")).doesNotContain(start);
        // Another patient cannot take the same slot.
        sendJson(post("/v1/scheduling/appointments", org.token()), Map.of("clinicId", clinic.toString(), "patientId", patientId(org, "Brian").toString(),
                "practitionerId", org.adminId().toString(), "startsAt", start), 409);
        send(post("/v1/scheduling/appointments/" + appt.get("id").asText() + "/cancel", org.token()).content("{\"reason\":\"Patient asked\"}"), 200);
        assertThat(fetch("/v1/scheduling/slots?clinicId=" + clinic + "&date=" + tomorrow(), org.token()).findValuesAsText("start")).contains(start);
    }

    @Test
    void tworequestsRacingForOneSlotProduceExactlyOneBooking() throws Exception {
        Org org = newOrg("race");
        UUID clinic = clinic(org, 30);
        String start = fetch("/v1/scheduling/slots?clinicId=" + clinic + "&date=" + tomorrow(), org.token()).get(10).get("start").asText();
        List<UUID> patients = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            patients.add(patientId(org, "Racer" + (char) ('A' + i)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (UUID patient : patients) {
            Callable<Integer> call = () -> mvc.perform(post("/v1/scheduling/appointments", org.token()).content(json.writeValueAsString(Map.of(
                    "clinicId", clinic.toString(), "patientId", patient.toString(), "practitionerId", org.adminId().toString(), "startsAt", start))))
                    .andReturn().getResponse().getStatus();
            results.add(pool.submit(call));
        }
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) {
            codes.add(f.get());
        }
        pool.shutdown();
        assertThat(codes.stream().filter(c -> c == 201)).hasSize(1);
        assertThat(codes.stream().filter(c -> c == 409)).hasSize(5);
    }

    @Test
    void slotsOutsideASessionAndPastTimesAreRefusedAndAPatientCannotBeInTwoPlaces() throws Exception {
        Org org = newOrg("rules");
        // Weekday sessions 09:00-10:00 only.
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            sessions.add(Map.of("practitionerId", org.adminId().toString(), "weekday", d, "startTime", "09:00:00", "endTime", "10:00:00"));
        }
        UUID clinic = UUID.fromString(create("/v1/scheduling/clinics", org.token(), Map.of("facilityId", org.facilityId().toString(), "name", "Morning", "slotMinutes", 20, "sessions", sessions)).get("id").asText());
        UUID patient = patientId(org, "Neema");
        JsonNode slots = fetch("/v1/scheduling/slots?clinicId=" + clinic + "&date=" + tomorrow(), org.token());
        assertThat(slots).hasSize(3);
        // 09:10 is not on the 20-minute grid.
        sendJson(post("/v1/scheduling/appointments", org.token()), Map.of("clinicId", clinic.toString(), "patientId", patient.toString(), "practitionerId", org.adminId().toString(),
                "startsAt", LocalDate.now(ZoneId.of("Africa/Nairobi")).plusDays(1) + "T09:10:00+03:00"), 400);
        sendJson(post("/v1/scheduling/appointments", org.token()), Map.of("clinicId", clinic.toString(), "patientId", patient.toString(), "practitionerId", org.adminId().toString(),
                "startsAt", "2020-01-06T09:00:00+03:00"), 400);
        // Same patient, a second clinic at the same moment.
        UUID other = clinic(org, 20);
        String start = slots.get(0).get("start").asText();
        create("/v1/scheduling/appointments", org.token(), Map.of("clinicId", clinic.toString(), "patientId", patient.toString(), "practitionerId", org.adminId().toString(), "startsAt", start));
        String second = userWithRoleAt(org);
        JsonNode r = sendJson(post("/v1/scheduling/appointments", org.token()), Map.of("clinicId", other.toString(), "patientId", patient.toString(),
                "practitionerId", org.adminId().toString(), "startsAt", start), 409);
        assertThat(r.get("code").asText()).isIn("slot_taken", "patient_double_booked");
        assertThat(second).isNotBlank();
    }

    private String userWithRoleAt(Org org) throws Exception {
        return userWithRole(org, "NURSE");
    }

    @Test
    void theQueueServesTheMostUrgentFirstAndCheckInIsTodayOnly() throws Exception {
        Org org = newOrg("queue");
        UUID clinic = clinic(org, 15);
        UUID routine = patientId(org, "Routine");
        UUID urgent = patientId(org, "Urgent");
        UUID priority = patientId(org, "Priority");
        create("/v1/scheduling/walk-ins", org.token(), Map.of("clinicId", clinic.toString(), "patientId", routine.toString(), "reason", "cough"));
        create("/v1/scheduling/walk-ins", org.token(), Map.of("clinicId", clinic.toString(), "patientId", priority.toString(), "priority", "PRIORITY"));
        JsonNode u = create("/v1/scheduling/walk-ins", org.token(), Map.of("clinicId", clinic.toString(), "patientId", urgent.toString(), "priority", "EMERGENCY"));
        JsonNode queue = fetch("/v1/scheduling/queue?facilityId=" + org.facilityId(), org.token());
        assertThat(queue).hasSize(3);
        assertThat(queue.get(0).get("patientId").asText()).isEqualTo(urgent.toString());
        assertThat(queue.get(1).get("patientId").asText()).isEqualTo(priority.toString());
        assertThat(queue.get(2).get("patientId").asText()).isEqualTo(routine.toString());
        assertThat(queue.get(2).get("queueNumber").asInt()).isEqualTo(1);
        assertThat(u.get("queueNumber").asInt()).isEqualTo(3);
        // Starting someone puts them at the head as in progress; finished ones leave the queue.
        String id = queue.get(2).get("id").asText();
        send(post("/v1/scheduling/appointments/" + id + "/start", org.token()), 200);
        assertThat(fetch("/v1/scheduling/queue?facilityId=" + org.facilityId(), org.token()).get(0).get("id").asText()).isEqualTo(id);
        send(post("/v1/scheduling/appointments/" + id + "/complete", org.token()), 200);
        assertThat(fetch("/v1/scheduling/queue?facilityId=" + org.facilityId(), org.token())).hasSize(2);
        // A finished appointment cannot be cancelled or restarted.
        send(post("/v1/scheduling/appointments/" + id + "/start", org.token()), 409);
        send(post("/v1/scheduling/appointments/" + id + "/cancel", org.token()).content("{\"reason\":\"oops\"}"), 409);
        // A booking for tomorrow cannot be checked in today.
        String start = fetch("/v1/scheduling/slots?clinicId=" + clinic + "&date=" + tomorrow(), org.token()).get(0).get("start").asText();
        JsonNode booked = create("/v1/scheduling/appointments", org.token(), Map.of("clinicId", clinic.toString(), "patientId", routine.toString(), "practitionerId", org.adminId().toString(), "startsAt", start));
        send(post("/v1/scheduling/appointments/" + booked.get("id").asText() + "/check-in", org.token()), 409);
    }

    @Test
    void appointmentsAreFacilityAndTenantScopedAndListedByKeysetPages() throws Exception {
        Org a = newOrg("sch-a");
        Org b = newOrg("sch-b");
        UUID clinic = clinic(a, 15);
        JsonNode slots = fetch("/v1/scheduling/slots?clinicId=" + clinic + "&date=" + tomorrow(), a.token());
        UUID patient = patientId(a, "Wairimu");
        String firstId = null;
        for (int i = 0; i < 5; i++) {
            JsonNode appt = create("/v1/scheduling/appointments", a.token(), Map.of("clinicId", clinic.toString(), "patientId", patient.toString(), "practitionerId", a.adminId().toString(), "startsAt", slots.get(i * 2).get("start").asText()));
            if (firstId == null) {
                firstId = appt.get("id").asText();
            }
        }
        JsonNode page1 = fetch("/v1/scheduling/appointments?limit=2&patientId=" + patient, a.token());
        assertThat(page1.get("data")).hasSize(2);
        JsonNode page2 = fetch("/v1/scheduling/appointments?limit=2&patientId=" + patient + "&cursor=" + page1.get("nextCursor").asText(), a.token());
        JsonNode page3 = fetch("/v1/scheduling/appointments?limit=2&patientId=" + patient + "&cursor=" + page2.get("nextCursor").asText(), a.token());
        assertThat(page3.get("data")).hasSize(1);
        assertThat(page3.has("nextCursor")).isFalse();
        // Another organisation sees none of it and cannot open it.
        send(get("/v1/scheduling/appointments/" + firstId, b.token()), 404);
        send(get("/v1/scheduling/slots?clinicId=" + clinic + "&date=" + tomorrow(), b.token()), 404);
        assertThat(fetch("/v1/scheduling/appointments", b.token()).get("data")).isEmpty();
        // A nurse in the org but not assigned to a second facility cannot use it.
        JsonNode second = create("/v1/facilities", a.token(), Map.of("name", "Second Site"));
        String nurse = userWithRole(a, "NURSE");
        send(get("/v1/scheduling/queue?facilityId=" + second.get("id").asText(), nurse), 403);
    }
}
