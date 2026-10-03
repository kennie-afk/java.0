package com.smartseason.ledger.platform;

import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RlsGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RlsGuard.class);

    private final JdbcTemplate jdbc;
    private final boolean rls;

    public RlsGuard(DataSource dataSource, @Value("${smartseason.tenancy.rls:true}") boolean rls) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.rls = rls;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!rls) {
            log.warn("Row-level security is switched off (smartseason.tenancy.rls=false). "
                    + "Tenant isolation rests on application filters alone.");
            return;
        }

        var role = jdbc.queryForMap(
                "select current_user as name, rolsuper, rolbypassrls from pg_roles where rolname = current_user");
        if (Boolean.TRUE.equals(role.get("rolsuper")) || Boolean.TRUE.equals(role.get("rolbypassrls"))) {
            throw new IllegalStateException("Refusing to start: database role '" + role.get("name")
                    + "' is a superuser or has BYPASSRLS, so row-level security would not apply to it. "
                    + "Connect the application as the unprivileged role (see docs/TENANCY.md) and keep "
                    + "the owner credentials for Flyway only (SPRING_FLYWAY_USER / SPRING_FLYWAY_PASSWORD).");
        }

        List<String> unprotected = jdbc.queryForList("""
                select c.relname
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public'
                  and c.relkind in ('r', 'p')
                  and not c.relispartition
                  and c.relname <> 'outbox_events'
                  and exists (select 1 from pg_attribute a
                              where a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped)
                  and (not c.relrowsecurity
                       or (pg_get_userbyid(c.relowner) = current_user and not c.relforcerowsecurity))
                order by c.relname
                """, String.class);
        if (!unprotected.isEmpty()) {
            throw new IllegalStateException("Refusing to start: tenant tables without effective "
                    + "row-level security for role '" + role.get("name") + "': " + unprotected
                    + ". Either RLS is not enabled on them (the Flyway callback db/callbacks/"
                    + "afterMigrate__tenant_isolation.sql runs as the owner) or this role owns them.");
        }
        log.info("Row-level security verified: role '{}' is unprivileged and every tenant table is protected.",
                role.get("name"));
    }
}
