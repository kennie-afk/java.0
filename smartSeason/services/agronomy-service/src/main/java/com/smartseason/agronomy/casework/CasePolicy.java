package com.smartseason.agronomy.casework;

import com.smartseason.agronomy.domain.Advisory.Severity;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The rules for how urgent a case is, how long an answer may take, and who gets it. */
public final class CasePolicy {

    /** Highest priority a caller-reported severity may earn; only the platform's own check could earn 4. */
    public static final int MAX_REPORTED_PRIORITY = 3;

    private CasePolicy() {
    }

    public static int priorityFor(Severity reported, AdvisoryCase.Reason reason) {
        int fromSeverity = reported == null ? 2 : switch (reported) {
            case CRITICAL -> 4;
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            case INFO -> 0;
        };
        // An unsure AI is itself a reason to look sooner than a routine question.
        if (reason == AdvisoryCase.Reason.LOW_CONFIDENCE) {
            fromSeverity = Math.max(fromSeverity, 2);
        }
        return Math.min(MAX_REPORTED_PRIORITY, fromSeverity);
    }

    /** Time allowed to answer: a spreading disease cannot wait three days. */
    public static Duration answerWithin(int priority) {
        return switch (priority) {
            case 4 -> Duration.ofHours(4);
            case 3 -> Duration.ofHours(24);
            case 2 -> Duration.ofHours(48);
            default -> Duration.ofHours(72);
        };
    }

    /**
     * The agronomist for a new case: among active agronomists who cover the county and are under
     * their limit, the one with the fewest cases in hand. Empty when nobody qualifies; the case
     * then waits in the shared queue instead of being forced onto someone already full.
     */
    public static Optional<AgronomistProfile> pick(List<AgronomistProfile> profiles,
                                                   Map<UUID, Long> openByUser, String county) {
        return profiles.stream()
                .filter(AgronomistProfile::isActive)
                .filter(p -> p.covers(county))
                .filter(p -> openByUser.getOrDefault(p.getUserId(), 0L) < p.getMaxOpen())
                .min(Comparator
                        // A specialist for the county beats a generalist who covers everywhere.
                        .comparingInt((AgronomistProfile p) -> p.countyList().isEmpty() ? 1 : 0)
                        .thenComparingLong(p -> openByUser.getOrDefault(p.getUserId(), 0L))
                        .thenComparing(p -> p.getUserId().toString()));
    }
}
