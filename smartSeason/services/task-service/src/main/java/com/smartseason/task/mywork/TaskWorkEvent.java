package com.smartseason.task.mywork;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of the TaskStarted / TaskStopped events. occurredAt is the same
 * server-stamped instant written to the assignment row, not a fresh
 * Instant.now() at publish time, so the audit entry and the assignment agree
 * to the microsecond.
 */
public record TaskWorkEvent(
        UUID assignmentId,
        UUID workerUserId,
        UUID actorUserId,
        String actorRole,
        Instant occurredAt) {
}
