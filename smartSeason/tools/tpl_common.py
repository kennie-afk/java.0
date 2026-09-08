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
