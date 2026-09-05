package com.smartseason.notification.dispatch;

import java.time.Instant;

public record DispatchDecision(
        Outcome outcome,
        String renderedSubject,
        String renderedBody,
        String locale,
        Instant deferredUntil,
        String reason) {

    public enum Outcome { SEND, DEFER, SUPPRESS }

    public boolean shouldSend() {
        return outcome == Outcome.SEND;
    }
}
