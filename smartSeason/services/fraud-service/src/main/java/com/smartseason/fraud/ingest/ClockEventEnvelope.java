package com.smartseason.fraud.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClockEventEnvelope(
        UUID eventId,
        String eventType,
        UUID tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String producer,
        Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID id,
            UUID workerId,
            UUID farmId,
            String eventType,
            Instant occurredAt,
            Double latitude,
            Double longitude,
            Double accuracyM,
            Double biometricScore,
            Boolean insideGeofence,
            Boolean mockLocation,
            String deviceId) {
    }
}
