package com.soko.notifications;

/**
 * Sends one email. There is exactly one method on purpose: every event this
 * system needs to tell someone about reduces to "this address, this subject,
 * this message" -- the composing of that message is the caller's job, not
 * this interface's.
 */
public interface EmailNotifier {
    void send(String to, String subject, String body);
}
