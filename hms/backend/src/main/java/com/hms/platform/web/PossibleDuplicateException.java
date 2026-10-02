package com.hms.platform.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Raised when a new record looks like one that already exists; carries the candidates to show. */
public class PossibleDuplicateException extends ApiException {
    private final transient List<?> candidates;

    public PossibleDuplicateException(List<?> candidates) {
        super(HttpStatus.CONFLICT, "possible_duplicate",
                "This looks like a patient who is already registered. Check the matches, or confirm this is a new person.");
        this.candidates = candidates;
    }

    public List<?> candidates() {
        return candidates;
    }
}
