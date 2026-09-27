package com.soko.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only {@link SmsSender} wired up right now. It sends nothing -- there is
 * no SMS provider configured, on purpose, because that needs a real account
 * (Africa's Talking is the standard choice for Kenya) that only the business
 * owner can supply. What it does instead is log exactly what would have been
 * sent and to whom, at INFO level, so an OTP flow is fully provable and
 * demoable before a single real text message goes out.
 *
 * <p>Swapping this for a real sender later is a one-class change: implement
 * {@link SmsSender} against whichever provider is chosen and mark it
 * {@code @Primary}, or remove this bean. Nothing that calls {@link SmsSender}
 * needs to change.
 */
@Component
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String phone, String body) {
        log.info("SMS not sent (no SMS provider configured) -- to: {}: {}", phone, body);
    }
}
