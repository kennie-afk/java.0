package com.kenyarealestate.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SendRateLimiter {

    private final int perMinute;
    private Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private int usedInWindow = 0;

    public SendRateLimiter(@Value("${notification.send-rate-per-minute:120}") int perMinute) {
        this.perMinute = perMinute;
    }

    public synchronized boolean tryAcquire() {
        Instant currentWindow = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        if (!currentWindow.equals(windowStart)) {
            windowStart = currentWindow;
            usedInWindow = 0;
        }
        if (usedInWindow >= perMinute) return false;
        usedInWindow++;
        return true;
    }
}
