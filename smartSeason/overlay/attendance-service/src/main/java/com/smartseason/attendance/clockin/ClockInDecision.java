package com.smartseason.attendance.clockin;

import java.util.List;
import java.util.UUID;

public record ClockInDecision(
        Verdict verdict,
        boolean insideGeofence,
        UUID matchedGeofenceId,
        Double distanceM,
        List<String> reasons) {

    public enum Verdict { ACCEPTED, FLAGGED, REJECTED }

    public boolean accepted() {
        return verdict == Verdict.ACCEPTED;
    }

    public String reasonSummary() {
        return reasons.isEmpty() ? null : String.join("; ", reasons);
    }
}
