package com.hms.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.platform.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes messages to the outbox inside the caller's transaction, and lets staff see what happened to them. */
@Service
public class NotificationService {

    public record Notice(UUID id, String channel, String recipient, String template, String status, int attempts, String lastError,
                         java.time.Instant createdAt, java.time.Instant sentAt) {}

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public NotificationService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Queues one message per contact the patient has on file (SMS to the phone, e-mail to the address). Returns what was queued, masked,
     * so the caller can tell staff. A patient with no contact simply gets nothing; that is not an error.
     */
    @Transactional
    public List<String> enqueueToPatient(UUID patientId, String template, Map<String, Object> params) {
        TenantContext.Tenant t = TenantContext.require();
        var contact = jdbc.sql("SELECT phone, email FROM patients WHERE org_id = ? AND id = ?").params(t.orgId(), patientId)
                .query((rs, n) -> new String[] {rs.getString("phone"), rs.getString("email")}).single();
        List<String> queued = new ArrayList<>();
        String paramsJson;
        try {
            paramsJson = json.writeValueAsString(params);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        for (String[] pair : new String[][] {{"SMS", contact[0]}, {"EMAIL", contact[1]}}) {
            if (pair[1] != null && pair[1].trim().length() >= 3) {
                jdbc.sql("INSERT INTO notification_outbox (org_id, patient_id, channel, recipient, template, params) VALUES (?, ?, ?, ?, ?, ?::jsonb)")
                        .params(t.orgId(), patientId, pair[0], pair[1].trim(), template, paramsJson).update();
                queued.add(pair[0] + " to " + NotificationProviders.mask(pair[1].trim()));
            }
        }
        return queued;
    }

    @Transactional(readOnly = true)
    public List<Notice> forPatient(UUID patientId, int limit) {
        TenantContext.Tenant t = TenantContext.require();
        return jdbc.sql("SELECT id, channel, recipient, template, status, attempts, last_error, created_at, sent_at FROM notification_outbox WHERE org_id = ? AND patient_id = ? ORDER BY created_at DESC, id DESC LIMIT ?")
                .params(t.orgId(), patientId, Math.max(1, Math.min(limit, 50)))
                .query((rs, n) -> new Notice(rs.getObject("id", UUID.class), rs.getString("channel"), NotificationProviders.mask(rs.getString("recipient")), rs.getString("template"),
                        rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"), rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject("sent_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("sent_at", java.time.OffsetDateTime.class).toInstant()))
                .list();
    }

    JsonNode parse(String s) {
        try {
            return json.readTree(s);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
