package com.hms.platform.security;

import java.util.UUID;

/** The patient a portal request belongs to, bound to the request thread beside the tenant. Portal code reads it and nothing else. */
public final class PortalSession {

    private static final ThreadLocal<UUID> PATIENT = new ThreadLocal<>();

    private PortalSession() {}

    static void set(UUID patientId) {
        PATIENT.set(patientId);
    }

    static void clear() {
        PATIENT.remove();
    }

    public static UUID patientId() {
        UUID id = PATIENT.get();
        if (id == null) {
            throw new IllegalStateException("no patient in scope; this ran outside an authenticated portal request");
        }
        return id;
    }
}
