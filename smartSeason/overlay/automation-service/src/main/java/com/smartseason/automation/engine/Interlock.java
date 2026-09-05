package com.smartseason.automation.engine;

import java.util.UUID;

public record Interlock(
        UUID deviceId,
        Type type,
        Integer maxRuntimeSeconds,
        UUID conflictingDeviceId,
        boolean engaged) {

    public enum Type { MAX_RUNTIME, MUTUAL_EXCLUSION, MANUAL_OVERRIDE }
}
