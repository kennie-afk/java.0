package com.mara.kit.auth;

import java.util.Optional;

/**
 * Where a server learns a terminal's public key, tenant and status. identity-service is the
 * only authority; a service never keeps its own copy of enrolment, so a revoked terminal stops
 * being believed everywhere within the cache window.
 */
public interface TerminalDirectory {

    Optional<TerminalRecord> find(String terminalId);
}
