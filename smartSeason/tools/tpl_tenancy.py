"""Templates for the database-level tenant isolation backstop.

Every tenant filter in the services is application code, and application code can miss one.
Row-level security makes Postgres refuse the query instead: the application role can see and
change only the rows of the tenant bound to its current transaction, and sees nothing at all
when none is bound.

Placeholders are __PKG__ (java package) and are substituted with str.replace, not format(),
so Java and SQL braces can be written as they are.
"""

TENANT_SESSION = '''package com.smartseason.__PKG__.platform;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Binds the current database transaction to a tenant.
 *
 * <p>Almost every request never touches this class: {@link TenantTransactionManager} binds
 * the tenant from {@link TenantContext} as each transaction begins. It is for the handful of
 * paths that only learn their tenant <em>after</em> a lookup inside an already-open
 * transaction - sign-in by e-mail, a refresh token, a payment provider's callback. Those look
 * up a tenant id through a narrow database function that returns nothing but the id, then
 * bind it here and carry on with ordinary, fully filtered queries.
 */
@Component
public class TenantSession {

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean rls;

    public TenantSession(@Value("${smartseason.tenancy.rls:true}") boolean rls) {
        this.rls = rls;
    }

    /** Binds the tenant for the rest of the current transaction and for this thread. */
    public void bind(UUID tenantId) {
        TenantContext.set(tenantId);
        if (rls && TransactionSynchronizationManager.isActualTransactionActive()) {
            apply(entityManager, tenantId);
        }
    }

    /** Transaction-local, so it is discarded at commit and never leaks across pooled connections. */
    static void apply(EntityManager em, UUID tenantId) {
        em.createNativeQuery("select set_config('app.tenant_id', :tenant, true)")
                .setParameter("tenant", tenantId == null ? "" : tenantId.toString())
                .getSingleResult();
    }
}
'''

TENANT_TX = '''package com.smartseason.__PKG__.platform;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Starts every transaction already bound to the caller's tenant.
 *
 * <p>The binding is {@code set_config(..., is_local = true)}: it lives exactly as long as the
 * transaction, so it is safe behind PgBouncer in transaction-pooling mode, where the next
 * transaction on the same server connection may belong to someone else. With no tenant in
 * {@link TenantContext} nothing is bound, and the row-level-security policies then show the
 * application role no rows at all.
 */
@Configuration
public class TenantTransactionManager {

    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            @Value("${smartseason.tenancy.rls:true}") boolean rls) {
        return new Binding(entityManagerFactory, rls);
    }

    static final class Binding extends JpaTransactionManager {

        private final boolean rls;

        Binding(EntityManagerFactory entityManagerFactory, boolean rls) {
            super(entityManagerFactory);
            this.rls = rls;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            if (!rls) {
                return;
            }
            UUID tenantId = TenantContext.tenantId().orElse(null);
            if (tenantId == null) {
                return;
            }
            EntityManagerHolder holder = (EntityManagerHolder)
                    TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            if (holder != null) {
                TenantSession.apply(holder.getEntityManager(), tenantId);
            }
        }
    }
}
'''

RLS_GUARD = '''package com.smartseason.__PKG__.platform;

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

/**
 * Refuses to start if row-level security would not actually protect anything.
 *
 * <p>Postgres exempts superusers, roles with BYPASSRLS and a table's own owner from RLS, so a
 * service that connects as one of those has isolation that looks configured and does nothing.
 * That is the failure that goes unnoticed for months, so it is checked at boot, loudly.
 */
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
'''

AFTER_MIGRATE = '''-- Tenant isolation: row-level security on every table that carries tenant_id.
--
-- Flyway runs this callback as the schema OWNER after every migrate, and it is idempotent:
-- a table that is already protected is left alone, so a restart takes no locks. Because it
-- discovers tables from the catalogue instead of listing them, a table added by any future
-- migration is protected the next time the service starts - there is no list to forget to
-- update. The service refuses to boot (RlsGuard) if one is still unprotected.
--
-- The policy is deliberately strict and index-friendly: a row is visible and writable only
-- when its tenant_id equals the tenant bound to the current transaction. There is no
-- "or system" escape hatch in the policy - an OR would stop Postgres using the tenant index.
-- The few paths that legitimately cross tenants use narrow SECURITY DEFINER functions that
-- return a tenant id and nothing else.
--
-- outbox_events is excluded on purpose: the relay publishes pending events for every tenant,
-- the table is never exposed through any endpoint, and each event already carries its tenant.

CREATE OR REPLACE FUNCTION app_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $fn$ SELECT nullif(current_setting('app.tenant_id', true), '')::uuid $fn$;

DO $rls$
DECLARE
    app_role text := '${app_role}';
    t record;
BEGIN
    FOR t IN
        SELECT c.oid, c.relname, c.relrowsecurity
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p')
          AND NOT c.relispartition
          AND c.relname <> 'outbox_events'
          AND EXISTS (SELECT 1 FROM pg_attribute a
                      WHERE a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped)
    LOOP
        IF NOT t.relrowsecurity THEN
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t.relname);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_policies p
                       WHERE p.schemaname = 'public' AND p.tablename = t.relname
                         AND p.policyname = 'tenant_isolation') THEN
            EXECUTE format(
                'CREATE POLICY tenant_isolation ON %I USING (tenant_id = app_tenant_id()) '
                'WITH CHECK (tenant_id = app_tenant_id())', t.relname);
        END IF;
    END LOOP;

    -- Privileges for the application role. Done here, not with ALTER DEFAULT PRIVILEGES, so a
    -- table or sequence created by any later migration is covered without a second step.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', app_role);
        FOR t IN
            SELECT c.oid, c.relname, c.relkind
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'S')
              AND c.relname <> 'flyway_schema_history'
        LOOP
            IF t.relkind = 'S' THEN
                IF NOT has_sequence_privilege(app_role, t.oid, 'USAGE') THEN
                    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %I TO %I', t.relname, app_role);
                END IF;
            ELSIF NOT has_table_privilege(app_role, t.oid, 'SELECT')
                  OR NOT has_table_privilege(app_role, t.oid, 'DELETE') THEN
                EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %I TO %I', t.relname, app_role);
            END IF;
        END LOOP;
        EXECUTE format('GRANT EXECUTE ON FUNCTION app_tenant_id() TO %I', app_role);
        FOR t IN
            SELECT p.oid::regprocedure AS sig
            FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public' AND p.proname LIKE 'ss\\_%'
        LOOP
            EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO %I', t.sig, app_role);
        END LOOP;
    END IF;
END
$rls$;
'''

REFERENCE_CHECKER = '''package com.smartseason.__PKG__.platform;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Confirms that an id a request points at belongs to the caller's own tenant.
 *
 * <p>A tenant-scoped finder already hides another tenant's rows when they are <em>read</em>.
 * Nothing used to stop a request <em>writing</em> another tenant's id into a reference field -
 * a plot under someone else's farm - which is an integrity hole and a probe: the response to a
 * guessed id reveals whether it exists. Generated services call this before they persist, so a
 * foreign id is refused exactly like a missing one, with the same message.
 *
 * <p>Only references to entities owned by this service can be checked here; a row owned by
 * another service lives in another database. See docs/TENANCY.md for which are covered.
 */
@Component
public class ReferenceChecker {

    /** Entity names come from the generator, never from a request; this is a second lock on the door. */
    private static final Pattern ENTITY = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean enabled;

    public ReferenceChecker() {
        this(true);
    }

    private ReferenceChecker(boolean enabled) {
        this.enabled = enabled;
    }

    /** A checker that accepts everything, for unit tests that have no database behind them. */
    public static ReferenceChecker disabled() {
        return new ReferenceChecker(false);
    }

    /** No-op for a null id (the field is optional); refuses an id outside the caller's tenant. */
    public void require(String entity, String field, UUID id) {
        if (!enabled || id == null) {
            return;
        }
        if (!ENTITY.matcher(entity).matches()) {
            throw new IllegalArgumentException("Not an entity name: " + entity);
        }
        Long found = entityManager
                .createQuery("select count(e) from " + entity + " e where e.id = :id and e.tenantId = :tenant",
                        Long.class)
                .setParameter("id", id)
                .setParameter("tenant", TenantContext.requireTenantId())
                .getSingleResult();
        if (found == null || found == 0L) {
            throw new DomainRuleException(field + " does not refer to a " + entity + " in your organisation");
        }
    }
}
'''

REFERENCE_CHECKER_TEST = '''package com.smartseason.__PKG__.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReferenceCheckerTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    @SuppressWarnings("unchecked")
    private final TypedQuery<Long> query = mock(TypedQuery.class);
    private final ReferenceChecker checker = new ReferenceChecker();
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(checker, "entityManager", entityManager);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an id that exists in the caller's tenant is accepted")
    void accepted() {
        when(query.getSingleResult()).thenReturn(1L);
        assertThatCode(() -> checker.require("Thing", "thingId", UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an id that is not in the caller's tenant is refused like a missing one")
    void refused() {
        when(query.getSingleResult()).thenReturn(0L);
        assertThatThrownBy(() -> checker.require("Thing", "thingId", UUID.randomUUID()))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("thingId");
    }

    @Test
    @DisplayName("an optional reference left empty is not checked")
    void optionalEmpty() {
        assertThatCode(() -> checker.require("Thing", "thingId", null)).doesNotThrowAnyException();
        assertThat(ReferenceChecker.disabled()).isNotNull();
    }

    @Test
    @DisplayName("the query is filtered by the caller's tenant")
    void queryCarriesTenant() {
        when(query.getSingleResult()).thenReturn(1L);
        checker.require("Thing", "thingId", UUID.randomUUID());
        org.mockito.Mockito.verify(query).setParameter("tenant", tenant);
    }

    @Test
    @DisplayName("something that is not an entity name never reaches the query")
    void entityNameIsConstrained() {
        assertThatThrownBy(() -> checker.require("Thing e where 1=1 or e", "thingId", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
'''
