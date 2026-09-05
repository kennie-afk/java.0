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

/**
 * Fields every persisted row in this service carries.
 *
 * <p>{{@code tenantId}} is present from the first migration so that tenant-aligned
 * sharding stays possible without a data-model change; {{@code version}} gives
 * optimistic locking on concurrent writes.
 */
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

/**
 * Request-scoped tenant identity, populated by {{@link JwtAuthenticationFilter}}.
 *
 * <p>Every repository query in this service is filtered by the value held here.
 * A missing tenant is a programming error, not a default-to-all: {{@link #requireTenantId()}}
 * throws rather than silently widening a query across tenants.
 */
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

/** Raised when a tenant-scoped operation runs without a resolved tenant. */
public class TenantMissingException extends RuntimeException {{

    public TenantMissingException() {{
        super("No tenant bound to the current request");
    }}
}}
'''

NOT_FOUND = '''package com.smartseason.{pkg}.platform;

import java.util.UUID;

/** Raised when a resource does not exist, or exists outside the caller's tenant. */
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

/** Raised when a request conflicts with current state (duplicate key, illegal transition). */
public class ConflictException extends RuntimeException {{

    public ConflictException(String message) {{
        super(message);
    }}
}}
'''

VALIDATION_EX = '''package com.smartseason.{pkg}.platform;

/** Raised when a request is syntactically valid but violates a domain rule. */
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into RFC 7807 {{@code application/problem+json}} responses.
 *
 * <p>Stack traces and driver messages never reach the client; they are logged
 * server-side and the caller gets a stable error code instead.
 */
@RestControllerAdvice
public class ApiExceptionHandler {{

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final URI BASE = URI.create("https://docs.smartseason.io/errors/");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail onNotFound(ResourceNotFoundException ex, HttpServletRequest request) {{
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage(), request);
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
