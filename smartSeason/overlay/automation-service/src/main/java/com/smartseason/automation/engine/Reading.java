package com.smartseason.automation.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Reading(
        UUID deviceId,
        UUID plotId,
        String metric,
        BigDecimal value,
        String unit,
        Instant recordedAt) {
}
