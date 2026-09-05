package com.smartseason.automation.engine;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TwinState(
        UUID plotId,
        String irrigationState,
        Instant lastIrrigatedAt,
        Instant runningSince,
        Map<UUID, Instant> deviceLastTriggered) {

    public static TwinState idle(UUID plotId) {
        return new TwinState(plotId, "IDLE", null, null, Map.of());
    }

    public boolean isRunning() {
        return "RUNNING".equals(irrigationState);
    }
}
