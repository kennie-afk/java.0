package com.smartseason.order.saga;

import java.util.List;

public record SagaOutcome(
        SagaStep reachedStep,
        boolean completed,
        boolean compensated,
        String failureReason,
        List<String> log) {

    public static SagaOutcome completed(List<String> log) {
        return new SagaOutcome(SagaStep.COMPLETE, true, false, null, List.copyOf(log));
    }

    public static SagaOutcome failed(SagaStep step, String reason, List<String> log) {
        return new SagaOutcome(step, false, true, reason, List.copyOf(log));
    }
}
