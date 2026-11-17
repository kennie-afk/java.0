package com.smartseason.agronomy.intelligence;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The AI model cannot answer. The service never substitutes a guess; it says so. */
public final class AdvisoryUnavailable {

    private AdvisoryUnavailable() {
    }

    public static ResponseStatusException notConfigured() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI diagnosis is not available: no AI model is configured. Ask an agronomist instead.");
    }

    public static ResponseStatusException noAnswer() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI diagnosis did not answer. Try again, or ask an agronomist.");
    }

    public static ResponseStatusException unreadable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI diagnosis returned an answer that could not be read. Try again, or ask an agronomist.");
    }
}
