package com.hms.platform.tenancy;

import com.hms.platform.config.HmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuses to start if the database role could ignore row-level security. A superuser or a role with
 * BYPASSRLS would make every tenant policy decorative, and nothing at runtime would notice.
 */
@Component
class RlsGuard implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RlsGuard.class);

    private final JdbcTemplate jdbc;
    private final HmsProperties props;

    RlsGuard(JdbcTemplate jdbc, HmsProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean privileged = jdbc.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
        if (Boolean.TRUE.equals(privileged)) {
            if (props.security().allowPrivilegedDbRole()) {
                log.warn("The database role bypasses row-level security. Tenant isolation is NOT enforced.");
                return;
            }
            throw new IllegalStateException(
                    "The API database role is a superuser or has BYPASSRLS, which defeats tenant isolation. "
                            + "Connect as the least-privilege application role.");
        }
    }
}
