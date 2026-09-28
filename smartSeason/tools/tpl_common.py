"""Shared platform Java sources, emitted into every service's com.smartseason.<pkg>.platform package.

Duplicated per service on purpose: each service is an independently buildable
artifact with no shared-jar release coupling (the same choice smartRE makes).
"""

BASE_ENTITY = '''package com.smartseason.{pkg}.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {{

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void onCreate() {{
        if (id == null) {{
            id = UUID.randomUUID();
        }}
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (tenantId == null) {{
            tenantId = TenantContext.requireTenantId();
        }}
    }}

    @PreUpdate
    void onUpdate() {{
        updatedAt = Instant.now();
    }}

    public UUID getId() {{ return id; }}
    public void setId(UUID id) {{ this.id = id; }}
    public UUID getTenantId() {{ return tenantId; }}
    public void setTenantId(UUID tenantId) {{ this.tenantId = tenantId; }}
    public Instant getCreatedAt() {{ return createdAt; }}
    public void setCreatedAt(Instant createdAt) {{ this.createdAt = createdAt; }}
    public Instant getUpdatedAt() {{ return updatedAt; }}
    public void setUpdatedAt(Instant updatedAt) {{ this.updatedAt = updatedAt; }}
    public long getVersion() {{ return version; }}
    public void setVersion(long version) {{ this.version = version; }}
}}
'''

TENANT_CONTEXT = '''package com.smartseason.{pkg}.platform;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {{

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {{
    }}

    public static void set(UUID tenantId) {{
        CURRENT.set(tenantId);
    }}

    public static Optional<UUID> tenantId() {{
        return Optional.ofNullable(CURRENT.get());
    }}

    public static UUID requireTenantId() {{
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {{
            throw new TenantMissingException();
        }}
        return tenantId;
    }}

    public static void clear() {{
        CURRENT.remove();
    }}
}}
'''

TENANT_MISSING = '''package com.smartseason.{pkg}.platform;

public class TenantMissingException extends RuntimeException {{

    public TenantMissingException() {{
        super("No tenant bound to the current request");
    }}
}}
'''

NOT_FOUND = '''package com.smartseason.{pkg}.platform;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {{

    public ResourceNotFoundException(String resource, UUID id) {{
        super(resource + " " + id + " not found");
    }}

    public ResourceNotFoundException(String message) {{
        super(message);
    }}
}}
'''

CONFLICT = '''package com.smartseason.{pkg}.platform;

public class ConflictException extends RuntimeException {{

    public ConflictException(String message) {{
        super(message);
    }}
}}
'''

VALIDATION_EX = '''package com.smartseason.{pkg}.platform;

public class DomainRuleException extends RuntimeException {{

    public DomainRuleException(String message) {{
        super(message);
    }}
}}
'''

EXCEPTION_HANDLER = '''package com.smartseason.{pkg}.platform;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {{

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final URI BASE = URI.create("https://docs.smartseason.io/errors/");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail onNotFound(ResourceNotFoundException ex, HttpServletRequest request) {{
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage(), request);
    }}

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail onUnknownPath(NoResourceFoundException ex, HttpServletRequest request) {{
        return problem(HttpStatus.NOT_FOUND, "not-found", "No endpoint at this path", request);
    }}

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail onMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {{
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "method-not-allowed",
                "That method is not supported on this path",
                request);
    }}

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail onConflict(ConflictException ex, HttpServletRequest request) {{
        return problem(HttpStatus.CONFLICT, "conflict", ex.getMessage(), request);
    }}

    @ExceptionHandler(DomainRuleException.class)
    public ProblemDetail onDomainRule(DomainRuleException ex, HttpServletRequest request) {{
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "domain-rule", ex.getMessage(), request);
    }}

    @ExceptionHandler(TenantMissingException.class)
    public ProblemDetail onTenantMissing(TenantMissingException ex, HttpServletRequest request) {{
        return problem(HttpStatus.UNAUTHORIZED, "tenant-missing", ex.getMessage(), request);
    }}

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex, HttpServletRequest request) {{
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Insufficient permissions", request);
    }}

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail onOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {{
        return problem(HttpStatus.CONFLICT, "stale-write",
                "The resource was modified by another request; reload and retry", request);
    }}

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {{
        log.warn("Data integrity violation on {{}}", request.getRequestURI(), ex);
        return problem(HttpStatus.CONFLICT, "constraint-violation",
                "The request violates a uniqueness or referential constraint", request);
    }}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {{
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "validation-failed",
                "One or more fields are invalid", request);
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fieldError -> errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        detail.setProperty("errors", errors);
        return detail;
    }}

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex, HttpServletRequest request) {{
        log.error("Unhandled exception on {{}} {{}}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "An unexpected error occurred", request);
    }}

    private ProblemDetail problem(HttpStatus status, String code, String message, HttpServletRequest request) {{
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(BASE.resolve(code));
        detail.setTitle(code);
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("code", code);
        return detail;
    }}
}}
'''

OUTBOX_ENTRY = '''package com.smartseason.{pkg}.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events", indexes = {{
        @Index(name = "ix_outbox_events_status", columnList = "status"),
        @Index(name = "ix_outbox_events_tenant", columnList = "tenant_id")
}})
public class OutboxEntry {{

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "message_key")
    private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public UUID getId() {{ return id; }}
    public void setId(UUID id) {{ this.id = id; }}
    public UUID getTenantId() {{ return tenantId; }}
    public void setTenantId(UUID tenantId) {{ this.tenantId = tenantId; }}
    public String getTopic() {{ return topic; }}
    public void setTopic(String topic) {{ this.topic = topic; }}
    public String getMessageKey() {{ return messageKey; }}
    public void setMessageKey(String messageKey) {{ this.messageKey = messageKey; }}
    public String getPayload() {{ return payload; }}
    public void setPayload(String payload) {{ this.payload = payload; }}
    public String getEventType() {{ return eventType; }}
    public void setEventType(String eventType) {{ this.eventType = eventType; }}
    public Status getStatus() {{ return status; }}
    public void setStatus(Status status) {{ this.status = status; }}
    public int getAttempts() {{ return attempts; }}
    public void setAttempts(int attempts) {{ this.attempts = attempts; }}
    public String getLastError() {{ return lastError; }}
    public void setLastError(String lastError) {{ this.lastError = lastError; }}
    public Instant getCreatedAt() {{ return createdAt; }}
    public void setCreatedAt(Instant createdAt) {{ this.createdAt = createdAt; }}
    public Instant getPublishedAt() {{ return publishedAt; }}
    public void setPublishedAt(Instant publishedAt) {{ this.publishedAt = publishedAt; }}

    public enum Status {{ PENDING, PUBLISHED, FAILED }}
}}
'''

OUTBOX_REPOSITORY = '''package com.smartseason.{pkg}.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {{

    List<OutboxEntry> findAllByStatusOrderByCreatedAtAsc(OutboxEntry.Status status, Pageable pageable);

    long countByStatus(OutboxEntry.Status status);

    /**
     * Removes entries that were published longer ago than the retention window.
     *
     * <p>Deliberately does not touch PENDING or FAILED rows: a FAILED entry is evidence
     * of something that never reached its consumer and is the first thing anyone will
     * look for, so it is kept until a person decides what to do with it.
     */
    @Modifying
    @Query("DELETE FROM OutboxEntry e WHERE e.status = :status AND e.publishedAt < :before")
    int deleteByStatusAndPublishedAtBefore(@Param("status") OutboxEntry.Status status,
                                           @Param("before") Instant before);
}}
'''

OUTBOX_RELAY = '''package com.smartseason.{pkg}.platform;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {{

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final boolean enabled;
    private final int batchSize;
    private final Duration retention;

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, Object> kafka,
                       @Value("${{smartseason.events.enabled:false}}") boolean enabled,
                       @Value("${{smartseason.events.relay-batch-size:100}}") int batchSize,
                       @Value("${{smartseason.events.retention-days:7}}") int retentionDays) {{
        this.outbox = outbox;
        this.kafka = kafka;
        this.enabled = enabled;
        this.batchSize = batchSize;
        // Seven days is long enough to replay after a consumer outage and short enough
        // that the table stays a queue rather than becoming an archive.
        this.retention = Duration.ofDays(retentionDays);
    }}

    /**
     * Deletes entries that were published longer ago than the retention window.
     *
     * <p>Without this the table only ever grows. Every write in this service emits an
     * event, so the outbox accumulates a row per business operation forever — across 27
     * services that is the largest table in each database within a year, holding rows
     * whose only remaining purpose is to have already been sent.
     *
     * <p>Hourly rather than on every relay pass: this is housekeeping, and running a
     * DELETE every two seconds to remove nothing is worse than useless.
     */
    @Scheduled(fixedDelayString = "${{smartseason.events.purge-interval-ms:3600000}}")
    @Transactional
    public void purgePublished() {{
        if (!enabled) {{
            return;
        }}
        int removed = outbox.deleteByStatusAndPublishedAtBefore(
                OutboxEntry.Status.PUBLISHED, Instant.now().minus(retention));
        if (removed > 0) {{
            log.info("Purged {{}} published outbox entries older than {{}}", removed, retention);
        }}
    }}

    @Scheduled(fixedDelayString = "${{smartseason.events.relay-interval-ms:2000}}")
    @Transactional
    public void relay() {{
        if (!enabled) {{
            return;
        }}

        List<OutboxEntry> pending = outbox.findAllByStatusOrderByCreatedAtAsc(
                OutboxEntry.Status.PENDING, PageRequest.of(0, batchSize));

        for (OutboxEntry entry : pending) {{
            try {{
                kafka.send(entry.getTopic(), entry.getMessageKey(), entry.getPayload()).get();
                entry.setStatus(OutboxEntry.Status.PUBLISHED);
                entry.setPublishedAt(Instant.now());
            }} catch (InterruptedException ex) {{
                // Only an actual interruption restores the flag. Setting it for every
                // failure — which this used to do — marks the scheduler thread as
                // interrupted because a broker was briefly unreachable, and every later
                // blocking call on that thread then fails for a reason that has nothing
                // to do with what went wrong.
                Thread.currentThread().interrupt();
                entry.setAttempts(entry.getAttempts() + 1);
                entry.setLastError("Relay interrupted");
                outbox.save(entry);
                return;
            }} catch (Exception ex) {{
                entry.setAttempts(entry.getAttempts() + 1);
                entry.setLastError(ex.getMessage());
                if (entry.getAttempts() >= MAX_ATTEMPTS) {{
                    entry.setStatus(OutboxEntry.Status.FAILED);
                    log.error("Outbox entry {{}} exhausted {{}} attempts and is parked for replay",
                            entry.getId(), MAX_ATTEMPTS);
                }}
            }}
            outbox.save(entry);
        }}
    }}
}}
'''

# Pays the first-request cost at start-up instead of charging it to a user.
#
# Measured on SmartRE on 2026-09-11, and the same shape applies here: the first call to
# an endpoint cost 20 to 200 times the second. A cold service answered in 3.53s what it
# then served in 17ms, and even a fresh endpoint on an already-warm service cost 117ms
# against a settled 9ms. None of that is query time - it is class loading, JIT,
# Hibernate building its metamodel and query plans, and Jackson constructing a
# serialiser per type.
#
# The cost is unavoidable; who pays it is a choice. Left alone it lands on whoever first
# touches a path after a deploy, a scale-up or an idle spell - which with an HPA that
# adds pods under load means it lands on users precisely when the system is busiest.
WARM_UP = '''package com.smartseason.{pkg}.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * Pays the first-request cost at start-up instead of charging it to a user.
 *
 * <p>Measured on the sibling SmartRE platform on 2026-09-11, and the same shape applies
 * here: the first call to an endpoint cost 20 to 200 times the second, worst case 3.53s
 * against a settled 17ms. None of it is query time - it is class loading, JIT, Hibernate
 * building its metamodel, and Jackson constructing a serialiser per type.
 *
 * <p>A first attempt primed only the repositories and cut the worst case to about a
 * third. The shortfall is worth recording: a request does not only touch the data layer,
 * it crosses the servlet container, the security filter chain, the controller and the
 * JSON writer. So all three layers are warmed here:
 *
 * <ol>
 *   <li><b>Data</b> - one row per repository, building the metamodel and materialising an
 *       entity.
 *   <li><b>Serialisation</b> - a Jackson serialiser per declared DTO, built from the type
 *       rather than from a row. Priming by serialising query results silently skips every
 *       DTO whose table is empty, which on a fresh deployment is most of them.
 *   <li><b>Transport</b> - one real loopback request, which starts the container's request
 *       path and runs the whole filter chain.
 * </ol>
 *
 * <p>A {{@link CommandLineRunner}}, not an {{@code ApplicationReadyEvent}} listener: runners
 * finish before the readiness probe reports up, so Kubernetes will not route to a pod that
 * has not warmed. Under an HPA that is the difference between absorbing a spike and
 * handing every new arrival the worst latency in the system.
 *
 * <p>Every failure is swallowed; a warm-up that blocked start-up would be worse than the
 * latency it removes.
 */
@Component
@Order(100)
public class WarmUp implements CommandLineRunner {{

    private static final Logger log = LoggerFactory.getLogger(WarmUp.class);

    private final ListableBeanFactory beans;
    private final ObjectMapper objectMapper;

    @Value("${{smartseason.warmup.enabled:true}}")
    private boolean enabled;

    @Value("${{server.port:8080}}")
    private int serverPort;

    public WarmUp(ListableBeanFactory beans, ObjectMapper objectMapper) {{
        this.beans = beans;
        this.objectMapper = objectMapper;
    }}

    @Override
    public void run(String... args) {{
        if (!enabled) {{
            log.info("Warm-up disabled; the first request to each path will pay for it.");
            return;
        }}
        long started = System.currentTimeMillis();
        int repositories = warmRepositories();
        int serialisers = warmSerialisers();
        boolean transport = warmTransport();
        log.info("Warm-up complete in {{}}ms: {{}} repositories, {{}} serialisers, transport={{}}",
                System.currentTimeMillis() - started, repositories, serialisers, transport);
    }}

    private int warmRepositories() {{
        int warmed = 0;
        for (Map.Entry<String, JpaRepository> entry
                : beans.getBeansOfType(JpaRepository.class).entrySet()) {{
            try {{
                entry.getValue().findAll(PageRequest.of(0, 1));
                warmed++;
            }} catch (Exception ex) {{
                log.debug("Warm-up skipped repository {{}}: {{}}", entry.getKey(), ex.getMessage());
            }}
        }}
        return warmed;
    }}

    private int warmSerialisers() {{
        // Plain string work rather than a regex: this template is itself rendered, and a
        // backslash that survives one layer and not the next produces Java that does not
        // compile. Nothing here needs escaping.
        String pkg = getClass().getPackageName();
        String suffix = ".platform";
        String base = pkg.endsWith(suffix) ? pkg.substring(0, pkg.length() - suffix.length()) : pkg;
        int warmed = 0;
        try {{
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
            for (var candidate : scanner.findCandidateComponents(base + ".web.dto")) {{
                try {{
                    Class<?> type = Class.forName(candidate.getBeanClassName());
                    objectMapper.getSerializerProviderInstance().findValueSerializer(type);
                    warmed++;
                }} catch (Throwable ignored) {{
                    // A DTO that will not serialise in isolation is not a start-up problem.
                }}
            }}
        }} catch (Exception ex) {{
            log.debug("Serialiser warm-up skipped: {{}}", ex.getMessage());
        }}
        return warmed;
    }}

    private boolean warmTransport() {{
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build()) {{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + serverPort + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        }} catch (Exception ex) {{
            log.debug("Transport warm-up skipped: {{}}", ex.getMessage());
            return false;
        }}
    }}
}}
'''

# Routes read-only transactions to a replica when one is configured.
#
# The 131 `@Transactional(readOnly = true)` annotations across the services were already
# there; nothing acted on them, so every read landed on the primary. This is the piece
# that makes them mean something.
READ_REPLICA = '''package com.smartseason.{pkg}.platform;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Sends read-only transactions to a read replica, writes to the primary.
 *
 * <p>Reads outnumber writes heavily here, and until now all of them landed on the single
 * primary, which is the ceiling the platform hits first. The routing key is Spring's own
 * {{@code readOnly}} transaction flag, so the services needed no change: a method already
 * marked {{@code @Transactional(readOnly = true)}} now reads from the replica.
 *
 * <h2>This is off unless a replica is configured</h2>
 * With {{@code smartseason.datasource.replica-url}} empty — the default, and the case
 * locally — this returns the ordinary primary {{@code DataSource}} with no wrapper and no
 * second pool. Behaviour is then byte-identical to having none of this code.
 *
 * <h2>The trade this makes, stated plainly</h2>
 * A replica lags its primary. A read routed to it can therefore miss a write that has
 * already committed, so a user who just saved something and immediately re-reads it may
 * not see it. That is acceptable for listing, searching and reporting, and is not
 * acceptable for a read that a decision is about to be made on.
 *
 * <p>Where it matters, wrap the read in {{@link #onPrimary}}, or simply do not mark the
 * method {{@code readOnly}}. Anything outside a transaction already goes to the primary,
 * because {{@code isCurrentTransactionReadOnly()}} is false there — the safe default.
 *
 * <p>Flyway is unaffected: migrations do not run in a read-only transaction, so they
 * route to the primary like any other write.
 */
@Configuration
public class ReadReplicaConfig {{

    private static final Logger log = LoggerFactory.getLogger(ReadReplicaConfig.class);

    private enum Route {{ PRIMARY, REPLICA }}

    private static final ThreadLocal<Boolean> FORCE_PRIMARY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Runs a read against the primary even inside a read-only transaction.
     *
     * <p>For the read-your-own-writes case: a caller that has just written and must see
     * its own change cannot tolerate replication lag.
     */
    public static <T> T onPrimary(Supplier<T> work) {{
        FORCE_PRIMARY.set(Boolean.TRUE);
        try {{
            return work.get();
        }} finally {{
            // remove(), not set(false): these threads are pooled and live for the life of
            // the process, so a stale value would pin every later request on this thread
            // to the primary and quietly undo the whole mechanism.
            FORCE_PRIMARY.remove();
        }}
    }}

    @Bean
    @Primary
    public DataSource dataSource(
            DataSourceProperties properties,
            @Value("${{smartseason.datasource.replica-host:}}") String replicaHost,
            @Value("${{DB_POOL_MAX:10}}") int poolMax) {{

        HikariDataSource primary = build(properties, properties.getUrl(), poolMax, "primary");

        if (!StringUtils.hasText(replicaHost)) {{
            log.info("No read replica configured; all reads go to the primary.");
            return primary;
        }}

        String replicaUrl = withHost(properties.getUrl(), replicaHost);
        if (replicaUrl == null) {{
            // Refusing to guess. A malformed replica URL would fail on the first read
            // rather than at start-up, and reads are most of the traffic.
            log.warn("Could not derive a replica URL from '{{}}'; staying on the primary.",
                    properties.getUrl());
            return primary;
        }}

        HikariDataSource replica = build(properties, replicaUrl, poolMax, "replica");

        Map<Object, Object> routes = new HashMap<>();
        routes.put(Route.PRIMARY, primary);
        routes.put(Route.REPLICA, replica);

        AbstractRoutingDataSource router = new AbstractRoutingDataSource() {{
            @Override
            protected Object determineCurrentLookupKey() {{
                if (Boolean.TRUE.equals(FORCE_PRIMARY.get())) {{
                    return Route.PRIMARY;
                }}
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        ? Route.REPLICA
                        : Route.PRIMARY;
            }}
        }};
        router.setTargetDataSources(routes);
        router.setDefaultTargetDataSource(primary);
        router.afterPropertiesSet();

        log.info("Read replica configured; read-only transactions will use it.");
        return router;
    }}

    /**
     * The primary URL with its host swapped for the replica's.
     *
     * <p>A host rather than a whole URL because each of the 27 services owns a different
     * database: one ConfigMap key can then serve all of them, where 27 full URLs could
     * not. Returns null rather than a guess if the URL is not the shape we expect.
     */
    static String withHost(String jdbcUrl, String replicaHost) {{
        if (jdbcUrl == null) {{
            return null;
        }}
        Matcher m = JDBC_HOST.matcher(jdbcUrl);
        if (!m.find()) {{
            return null;
        }}
        // Only the authority is replaced: the database name, the port and every query
        // parameter are kept, and prepareThreshold=0 among them is not optional with
        // transaction pooling.
        return m.group(1) + replicaHost + m.group(3);
    }}

    private static final Pattern JDBC_HOST =
            Pattern.compile("^(jdbc:postgresql://)([^/:?]+)(.*)$");

    private HikariDataSource build(DataSourceProperties properties, String url, int poolMax, String name) {{
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .url(url)
                .build();
        ds.setPoolName("{pkg}-" + name);
        // Each pool is sized as though it were the only one. Two pools therefore double
        // this service's worst-case connection count, which is why PgBouncer sits in
        // front of both and why max_connections is a budget, not a formality.
        ds.setMaximumPoolSize(poolMax);
        ds.setMinimumIdle(2);
        return ds;
    }}
}}
'''
