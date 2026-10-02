package com.mara.kit.auth;

/** What a server needs to know about an enrolled terminal, as identity-service reports it. */
public record TerminalRecord(String id, String tenantId, String branchId, String publicKey, String status) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
