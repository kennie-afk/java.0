package com.soko.notifications;

/**
 * Sends one SMS. Exists specifically for OTP codes right now, but is
 * deliberately generic (a phone number and a body, nothing OTP-specific) so
 * it can carry other notifications later without a new interface.
 */
public interface SmsSender {
    void send(String phone, String body);
}
