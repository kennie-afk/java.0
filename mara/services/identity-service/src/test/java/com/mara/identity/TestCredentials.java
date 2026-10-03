package com.mara.identity;

import com.mara.platform.credential.OperatorToken;
import org.springframework.jdbc.core.JdbcTemplate;

/** The credentials every identity integration test uses, minted once per JVM. */
public final class TestCredentials {

    public static final String PLATFORM_TOKEN = OperatorToken.mint().token();
    public static final String SVC_CORE_TOKEN = OperatorToken.mint().token();
    public static final String SVC_SYNC_TOKEN = OperatorToken.mint().token();

    private TestCredentials() {
    }

    /** A platform operator that may do everything an operator can: inserted directly, as an owner would. */
    public static void installPlatformOperator(JdbcTemplate owner) {
        var parsed = OperatorToken.parse(PLATFORM_TOKEN).orElseThrow();
        owner.update("""
                INSERT INTO operator_credential (key_id, secret_hash, label, kind, tenant_id, scopes, created_by, expires_at)
                VALUES (?, ?, 'test-platform-operator', 'OPERATOR', NULL,
                        ARRAY['admin:read','admin:write','platform:tenants','credentials:manage'], 'test', now() + interval '1 day')
                ON CONFLICT (key_id) DO NOTHING""", parsed.keyId(), OperatorToken.hash(parsed.secret()));
    }
}
