package com.kenyarealestate.notification.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SendRateLimiterTest {

    @Test
    void allowsUpToTheCapThenRefuses() {
        SendRateLimiter limiter = new SendRateLimiter(3);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "the fourth send in the window must be deferred, not sent");
    }

    @Test
    void aZeroCapRefusesEverything() {
        assertFalse(new SendRateLimiter(0).tryAcquire());
    }
}
