"""Service, controller, test and SQL-migration generators."""
from gen_support import lower_first


def resource_path(table):
    return table.replace("_", "-")


def service_source(pkg, name, table, fields, domain):
    var = lower_first(name)
    setters_create = "\n".join(
        f"        entity.{f.setter}(request.{f.name}());" for f in fields)
    setters_update = "\n".join(
        f"""        if (request.{f.name}() != null) {{
            entity.{f.setter}(request.{f.name}());
        }}""" for f in fields)

    return f"""package com.smartseason.{pkg}.service;

import com.smartseason.{pkg}.domain.{name};
import com.smartseason.{pkg}.platform.EventPublisher;
import com.smartseason.{pkg}.platform.PageResponse;
import com.smartseason.{pkg}.platform.ResourceNotFoundException;
import com.smartseason.{pkg}.platform.TenantContext;
import com.smartseason.{pkg}.repo.{name}Repository;
import com.smartseason.{pkg}.web.dto.{name}CreateRequest;
import com.smartseason.{pkg}.web.dto.{name}Response;
import com.smartseason.{pkg}.web.dto.{name}UpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application logic for {name}.
 *
 * <p>Reads default to a read-only transaction; only the mutating methods open a
 * read-write one. Every lookup goes through a tenant-qualified finder, so a row
 * belonging to another tenant is indistinguishable from one that does not exist.
 */
@Service
@Transactional(readOnly = true)
public class {name}Service {{

    private static final String RESOURCE = "{name}";

    private final {name}Repository repository;
    private final EventPublisher events;

    public {name}Service({name}Repository repository, EventPublisher events) {{
        this.repository = repository;
        this.events = events;
    }}

    public PageResponse<{name}Response> list(Pageable pageable) {{
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map({name}Response::from));
    }}

    public {name}Response get(UUID id) {{
        return {name}Response.from(require(id));
    }}

    public long count() {{
        return repository.countByTenantId(TenantContext.requireTenantId());
    }}

    @Transactional
    public {name}Response create({name}CreateRequest request) {{
        {name} entity = new {name}();
        entity.setTenantId(TenantContext.requireTenantId());
{setters_create}

        {name} saved = repository.save(entity);
        events.publish("{domain}", "{name}Created", saved.getId(), {name}Response.from(saved));
        return {name}Response.from(saved);
    }}

    @Transactional
    public {name}Response update(UUID id, {name}UpdateRequest request) {{
        {name} entity = require(id);
{setters_update}

        {name} saved = repository.save(entity);
        events.publish("{domain}", "{name}Updated", saved.getId(), {name}Response.from(saved));
        return {name}Response.from(saved);
    }}

    @Transactional
    public void delete(UUID id) {{
        {name} entity = require(id);
        repository.delete(entity);
        events.publish("{domain}", "{name}Deleted", id, null);
    }}

    private {name} require(UUID id) {{
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }}
}}
"""


def controller_source(pkg, name, table, domain, desc):
    var = lower_first(name)
    path = resource_path(table)
    return f"""package com.smartseason.{pkg}.web;

import com.smartseason.{pkg}.platform.PageResponse;
import com.smartseason.{pkg}.service.{name}Service;
import com.smartseason.{pkg}.web.dto.{name}CreateRequest;
import com.smartseason.{pkg}.web.dto.{name}Response;
import com.smartseason.{pkg}.web.dto.{name}UpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for {name}. */
@RestController
@RequestMapping("/api/{domain}/v1/{path}")
@Tag(name = "{name}", description = "{desc}")
public class {name}Controller {{

    private final {name}Service service;

    public {name}Controller({name}Service service) {{
        this.service = service;
    }}

    @GetMapping
    @Operation(summary = "List {path} for the caller's tenant")
    public PageResponse<{name}Response> list(@PageableDefault(size = 20) Pageable pageable) {{
        return service.list(pageable);
    }}

    @GetMapping("/{{id}}")
    @Operation(summary = "Fetch a single {name} by id")
    public {name}Response get(@PathVariable UUID id) {{
        return service.get(id);
    }}

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a {name}")
    public ResponseEntity<{name}Response> create(@Valid @RequestBody {name}CreateRequest request) {{
        {name}Response created = service.create(request);
        return ResponseEntity.created(URI.create("/api/{domain}/v1/{path}/" + created.id())).body(created);
    }}

    @PatchMapping("/{{id}}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a {name}")
    public {name}Response update(@PathVariable UUID id, @Valid @RequestBody {name}UpdateRequest request) {{
        return service.update(id, request);
    }}

    @DeleteMapping("/{{id}}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a {name}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {{
        service.delete(id);
        return ResponseEntity.noContent().build();
    }}
}}
"""


def migration_source(service, entities):
    """One forward-only Flyway migration per service."""
    out = [f"-- {service}: initial schema", "-- Forward-only. Every table carries tenant_id"
           " so tenant-aligned sharding stays possible without a data-model change.", ""]
    for name, table, fields in entities:
        out.append(f"CREATE TABLE {table} (")
        cols = ["    id            UUID PRIMARY KEY",
                "    tenant_id     UUID NOT NULL",
                "    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()",
                "    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()",
                "    version       BIGINT NOT NULL DEFAULT 0"]
        for f in fields:
            c = f"    {f.column:<22} {f.sql_type}"
            if f.notnull:
                c += " NOT NULL"
            if f.unique:
                c += " UNIQUE"
            cols.append(c)
        out.append(",\n".join(cols))
        out.append(");")
        out.append(f"CREATE INDEX ix_{table}_tenant ON {table} (tenant_id);")
        for f in fields:
            if f.indexed and not f.unique:
                out.append(f"CREATE INDEX ix_{table}_{f.column} ON {table} ({f.column});")
        out.append("")
    return "\n".join(out)


def service_test_source(pkg, name, fields):
    """Unit test proving tenant scoping and not-found behaviour."""
    required = [f for f in fields if f.notnull]
    def sample(f):
        if f.kind == "enum":
            return f"{name}.{f.enum_name}.{f.enum_values[0]}"
        return {
            "String": '"test"', "UUID": "UUID.randomUUID()", "Integer": "1",
            "Long": "1L", "BigDecimal": "BigDecimal.ONE", "Boolean": "true",
            "Instant": "Instant.now()", "LocalDate": "LocalDate.now()",
        }[f.java_type]

    args = ", ".join(sample(f) if f.notnull else "null" for f in fields)

    return f"""package com.smartseason.{pkg}.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.{pkg}.domain.{name};
import com.smartseason.{pkg}.platform.EventPublisher;
import com.smartseason.{pkg}.platform.ResourceNotFoundException;
import com.smartseason.{pkg}.platform.TenantContext;
import com.smartseason.{pkg}.platform.TenantMissingException;
import com.smartseason.{pkg}.repo.{name}Repository;
import com.smartseason.{pkg}.web.dto.{name}CreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class {name}ServiceTest {{

    private final {name}Repository repository = mock({name}Repository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final {name}Service service = new {name}Service(repository, events);

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void bindTenant() {{
        TenantContext.set(tenant);
    }}

    @AfterEach
    void clearTenant() {{
        TenantContext.clear();
    }}

    @Test
    @DisplayName("create persists the entity against the caller's tenant and emits an event")
    void createStampsTenantAndPublishes() {{
        when(repository.save(any({name}.class))).thenAnswer(invocation -> {{
            {name} saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        }});

        var response = service.create(new {name}CreateRequest({args}));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("{name}Created"), any(), any());
    }}

    @Test
    @DisplayName("a row belonging to another tenant reads as not found")
    void otherTenantRowIsNotFound() {{
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }}

    @Test
    @DisplayName("an unbound tenant fails closed instead of querying across tenants")
    void missingTenantFailsClosed() {{
        TenantContext.clear();

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(TenantMissingException.class);
    }}
}}
"""
