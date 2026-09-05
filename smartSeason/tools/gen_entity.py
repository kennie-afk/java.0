"""Builds the per-entity Java sources: JPA entity, repository, DTOs, service, controller, test."""
from gen_support import Field, snake, lower_first

IMPORT_FOR = {
    "UUID": "java.util.UUID",
    "BigDecimal": "java.math.BigDecimal",
    "Instant": "java.time.Instant",
    "LocalDate": "java.time.LocalDate",
}


def ref_type(f, entity):
    """Enums are declared nested inside the entity, so DTOs and repositories
    must qualify them (Farm.Status) rather than naming them bare."""
    return f"{entity}.{f.enum_name}" if f.kind == "enum" else f.java_type


def _imports(fields, extra=()):
    needed = set(extra)
    for f in fields:
        t = f.java_type
        if t in IMPORT_FOR:
            needed.add(IMPORT_FOR[t])
    return sorted(needed)


def entity_source(pkg, name, table, fields):
    lines = [f"package com.smartseason.{pkg}.domain;", ""]
    lines += ["import com.smartseason.%s.platform.BaseEntity;" % pkg]
    lines += ["import jakarta.persistence.Column;",
              "import jakarta.persistence.Entity;",
              "import jakarta.persistence.EnumType;",
              "import jakarta.persistence.Enumerated;",
              "import jakarta.persistence.Index;",
              "import jakarta.persistence.Table;"]
    if any(f.kind == "json" for f in fields):
        lines += ["import org.hibernate.annotations.JdbcTypeCode;",
                  "import org.hibernate.type.SqlTypes;"]
    for imp in _imports(fields):
        lines.append(f"import {imp};")
    lines.append("")

    idx = [f for f in fields if f.indexed]
    index_defs = ",\n        ".join(
        f'@Index(name = "ix_{table}_{f.column}", columnList = "{f.column}")' for f in idx)
    table_ann = f'@Table(name = "{table}"'
    if index_defs:
        table_ann += f", indexes = {{\n        {index_defs}\n}}"
    table_ann += ")"

    lines += [f"/** JPA mapping for {{@code {table}}}. */", "@Entity", table_ann,
              f"public class {name} extends BaseEntity {{", ""]

    for f in fields:
        col = f'name = "{f.column}"'
        if f.notnull:
            col += ", nullable = false"
        if f.unique:
            col += ", unique = true"
        if f.kind == "text":
            col += ', columnDefinition = "TEXT"'
        if f.kind == "json":
            col += ', columnDefinition = "jsonb"'
            lines.append("    @JdbcTypeCode(SqlTypes.JSON)")
        if f.kind == "enum":
            lines.append("    @Enumerated(EnumType.STRING)")
        lines.append(f"    @Column({col})")
        lines.append(f"    private {f.java_type} {f.name};")
        lines.append("")

    for f in fields:
        lines.append(f"    public {f.java_type} {f.getter}() {{ return {f.name}; }}")
        lines.append(f"    public void {f.setter}({f.java_type} {f.name}) {{ this.{f.name} = {f.name}; }}")
        lines.append("")

    for f in fields:
        if f.kind == "enum":
            values = ", ".join(f.enum_values)
            lines.append(f"    /** Permitted values for {{@code {f.column}}}. */")
            lines.append(f"    public enum {f.enum_name} {{ {values} }}")
            lines.append("")

    lines.append("}")
    return "\n".join(lines) + "\n"


def repository_source(pkg, name, fields):
    var = lower_first(name)
    finders = []
    for f in fields:
        if not f.indexed:
            continue
        if f.unique:
            finders.append(
                f"    Optional<{name}> findBy{f.name[0].upper() + f.name[1:]}AndTenantId("
                f"{ref_type(f, name)} {f.name}, UUID tenantId);\n")
        else:
            finders.append(
                f"    Page<{name}> findAllBy{f.name[0].upper() + f.name[1:]}AndTenantId("
                f"{ref_type(f, name)} {f.name}, UUID tenantId, Pageable pageable);\n")

    extra_imports = ""
    if any(f.java_type in IMPORT_FOR and f.java_type != "UUID" for f in fields if f.indexed):
        for imp in sorted({IMPORT_FOR[f.java_type] for f in fields
                           if f.indexed and f.java_type in IMPORT_FOR and f.java_type != "UUID"}):
            extra_imports += f"import {imp};\n"

    return f"""package com.smartseason.{pkg}.repo;

import com.smartseason.{pkg}.domain.{name};
{extra_imports}import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access for {{@link {name}}}.
 *
 * <p>Every method is tenant-qualified. There is deliberately no bare {{@code findById}}
 * exposed to callers: a lookup that forgets the tenant is a cross-tenant data leak, so the
 * compiler is used to make that mistake impossible rather than relying on review.
 */
@Repository
public interface {name}Repository extends JpaRepository<{name}, UUID> {{

    Optional<{name}> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<{name}> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

{''.join(finders)}}}
"""


def _dto_field_decl(f, for_create, entity):
    """Validation annotations belong on the inbound request records only."""
    ann = []
    if for_create and f.notnull:
        if f.java_type == "String":
            ann.append("@NotBlank")
        else:
            ann.append("@NotNull")
    if f.java_type == "String" and not f.is_text:
        ann.append("@Size(max = 255)")
    prefix = " ".join(ann)
    return (prefix + " " if prefix else "") + f"{ref_type(f, entity)} {f.name}"


def response_dto_source(pkg, name, fields):
    body = ",\n        ".join(f"{ref_type(f, name)} {f.name}" for f in fields)
    imports = "".join(f"import {i};\n" for i in _imports(fields, extra=["java.util.UUID", "java.time.Instant"]))
    ref = ",\n                ".join(f"entity.{f.getter}()" for f in fields)
    return f"""package com.smartseason.{pkg}.web.dto;

import com.smartseason.{pkg}.domain.{name};
{imports}
/** Outbound representation of a {name}. */
public record {name}Response(
        UUID id,
        {body},
        Instant createdAt,
        Instant updatedAt) {{

    public static {name}Response from({name} entity) {{
        return new {name}Response(
                entity.getId(),
                {ref},
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }}
}}
"""


def request_dto_source(pkg, name, fields, kind):
    """kind: 'Create' (validated, required fields enforced) or 'Update' (all optional)."""
    for_create = kind == "Create"
    body = ",\n        ".join(_dto_field_decl(f, for_create, name) for f in fields)
    imports = "".join(f"import {i};\n" for i in _imports(fields))
    note = ("Fields marked required here mirror the NOT NULL constraints in the migration,"
            " so a bad request fails at the edge instead of at the database."
            if for_create else
            "All fields are optional; only non-null values are applied to the target.")
    return f"""package com.smartseason.{pkg}.web.dto;

import com.smartseason.{pkg}.domain.{name};
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
{imports}
/**
 * Inbound payload to {kind.lower()} a {name}.
 *
 * <p>{note}
 */
public record {name}{kind}Request(
        {body}) {{
}}
"""
