package com.mara.platform.credential;

import java.util.List;
import java.util.Set;

/**
 * What a credential may do. Narrow on purpose: a credential that can look a terminal up cannot
 * provision a tenant, one that reads a tenant's books cannot write to them, and nothing short of
 * a platform credential can create tenants or mint other credentials.
 */
public final class Scopes {

    /** Read the back office (lists, audit trail, ledger reads) of the credential's tenant. */
    public static final String ADMIN_READ = "admin:read";
    /** Change things in the back office: staff, branches, enrolment codes, statuses. */
    public static final String ADMIN_WRITE = "admin:write";
    /** Create a tenant. Platform credentials only (no tenant binding). */
    public static final String PLATFORM_TENANTS = "platform:tenants";
    /** Issue, rotate, revoke and list credentials. Platform credentials only. */
    public static final String CREDENTIALS_MANAGE = "credentials:manage";
    /** Service to service: look a terminal's public key and status up. */
    public static final String TERMINALS_LOOKUP = "terminals:lookup";
    /** Service to service: ask identity whether a presented credential is valid. */
    public static final String CREDENTIALS_VERIFY = "credentials:verify";
    /** Service to service: read the verified journal feed from sync-service. */
    public static final String SYNC_FEED = "sync:feed";

    public static final Set<String> ALL = Set.of(
            ADMIN_READ, ADMIN_WRITE, PLATFORM_TENANTS, CREDENTIALS_MANAGE, TERMINALS_LOOKUP, CREDENTIALS_VERIFY, SYNC_FEED);

    /** Held only by credentials with no tenant binding. */
    public static final Set<String> PLATFORM_ONLY = Set.of(PLATFORM_TENANTS, CREDENTIALS_MANAGE);

    /** Held only by service credentials, never by a person's. */
    public static final Set<String> SERVICE_ONLY = Set.of(TERMINALS_LOOKUP, CREDENTIALS_VERIFY, SYNC_FEED);

    private Scopes() {
    }

    /**
     * Why a credential of this kind and binding may not hold these scopes, or null if it may.
     * Enforced when it is issued, so a stored credential can never exceed what its kind allows.
     */
    public static String problem(String kind, String tenantId, List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "a credential needs at least one scope";
        }
        for (String scope : scopes) {
            if (!ALL.contains(scope)) {
                return "unknown scope " + scope;
            }
            if (PLATFORM_ONLY.contains(scope) && tenantId != null) {
                return scope + " is for platform credentials and cannot be tenant-bound";
            }
            if (SERVICE_ONLY.contains(scope) && !"SERVICE".equals(kind)) {
                return scope + " is for service credentials only";
            }
            if (!SERVICE_ONLY.contains(scope) && "SERVICE".equals(kind)) {
                return scope + " is for operator credentials, not services";
            }
        }
        return null;
    }
}
