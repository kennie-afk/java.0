package com.kenyarealestate.verification.enums;

public enum ReportReason {
    FAKE_LISTING,
    // Kept deliberately after the AGENT role was removed. The platform no longer has
    // agents, but people still misrepresent themselves as one to a buyer, and that is
    // exactly the behaviour a buyer needs to be able to report.
    SCAM_AGENT,
    DUPLICATE_LISTING,
    OFF_PLATFORM_PAYMENT_REQUEST,
    FAKE_REVIEW,
    OTHER
}
