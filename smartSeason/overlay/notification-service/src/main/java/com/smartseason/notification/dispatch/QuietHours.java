package com.smartseason.notification.dispatch;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record QuietHours(LocalTime start, LocalTime end, ZoneId zone) {

    public static QuietHours eastAfricaNight() {
        return new QuietHours(LocalTime.of(21, 0), LocalTime.of(7, 0),
                ZoneId.of("Africa/Nairobi"));
    }

    public boolean covers(Instant instant) {
        LocalTime local = ZonedDateTime.ofInstant(instant, zone).toLocalTime();
        if (start.isBefore(end)) {
            return !local.isBefore(start) && local.isBefore(end);
        }
        return !local.isBefore(start) || local.isBefore(end);
    }

    public Instant nextOpening(Instant instant) {
        ZonedDateTime local = ZonedDateTime.ofInstant(instant, zone);
        ZonedDateTime opening = local.with(end);
        if (!opening.isAfter(local)) {
            opening = opening.plusDays(1);
        }
        return opening.toInstant();
    }
}
