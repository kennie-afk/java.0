#!/usr/bin/env python3
"""Emits the SmartSeason Java microservices tree from the catalogue."""
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalogue import SERVICES
from gen_support import parse_entity, pkg_of, kebab_to_pascal
import gen_entity as ge
import strip_comments as sc
import gen_layers as gl
import tpl_platform as tp
import tpl_common as tc
import tpl_security as ts

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# identity-service exposes unauthenticated auth endpoints; every other service is closed.
RESERVED_FIELDS = {"id", "tenantId", "createdAt", "updatedAt", "version"}

PUBLIC_MATCHERS = {
    "identity-service": '                .requestMatchers("/api/identity/v1/auth/**").permitAll()\n'
                        '                .requestMatchers("/api/identity/v1/.well-known/**").permitAll()\n',
}
EXTRA_DEPS = {
    "identity-service": "    <dependency><groupId>org.springframework.boot</groupId>"
                        "<artifactId>spring-boot-starter-mail</artifactId></dependency>\n",
}


def write(path, content):
    if path.endswith(".java"):
        content = sc.strip_java(content)
    elif path.endswith(".sql"):
        content = sc.strip_sql(content)
    elif os.path.basename(path) == "Dockerfile":
        content = sc.strip_hash(content)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        handle.write(content)
    return path


def generate_service(spec):
    service = spec["name"]
    pkg = pkg_of(service)
    clazz = kebab_to_pascal(service) + "Application"
    port = spec["port"]
    db = spec["db"]
    desc = spec["desc"]
    domain = spec["group"]
    entities = [parse_entity(e) for e in spec["entities"]]
    slug = service[:-len("-service")] if service.endswith("-service") else service

    for entity_name, _table, entity_fields in entities:
        for field in entity_fields:
            if field.name in RESERVED_FIELDS:
                raise ValueError(
                    f"{service}/{entity_name}: field '{field.name}' collides with BaseEntity")

    base = os.path.join(ROOT, "services", service)
    src = os.path.join(base, "src/main/java/com/smartseason", pkg)
    test = os.path.join(base, "src/test/java/com/smartseason", pkg)
    res = os.path.join(base, "src/main/resources")
    written = []

    written.append(write(os.path.join(base, "pom.xml"),
                         tp.POM.format(service=service, desc=desc,
                                       extra_deps=EXTRA_DEPS.get(service, ""))))
    written.append(write(os.path.join(base, "Dockerfile"), tp.DOCKERFILE.format(port=port)))
    written.append(write(os.path.join(res, "application.yaml"),
                         tp.APPLICATION_YAML.format(service=service, db=db, port=port)))
    written.append(write(os.path.join(base, "src/test/resources/application.yaml"),
                         tp.APPLICATION_TEST_YAML.format(db=db, service=service)))
    written.append(write(os.path.join(res, "db/migration/V1__init.sql"),
                         gl.migration_source(service, entities)))
    written.append(write(os.path.join(src, clazz + ".java"),
                         tp.MAIN_CLASS.format(pkg=pkg, clazz=clazz, desc=desc, db=db)))
    written.append(write(os.path.join(test, clazz + "Test.java"),
                         tp.CONTEXT_TEST.format(pkg=pkg, clazz=clazz)))

    # platform package
    plat = os.path.join(src, "platform")
    for filename, template in [
        ("BaseEntity.java", tc.BASE_ENTITY), ("TenantContext.java", tc.TENANT_CONTEXT),
        ("TenantMissingException.java", tc.TENANT_MISSING),
        ("ResourceNotFoundException.java", tc.NOT_FOUND),
        ("ConflictException.java", tc.CONFLICT),
        ("DomainRuleException.java", tc.VALIDATION_EX),
        ("ApiExceptionHandler.java", tc.EXCEPTION_HANDLER),
        ("JwtAuthenticationFilter.java", ts.JWT_FILTER),
        ("PageResponse.java", ts.PAGE_RESPONSE),
        ("DomainEvent.java", ts.DOMAIN_EVENT),
        ("EventPublisher.java", ts.EVENT_PUBLISHER),
    ]:
        written.append(write(os.path.join(plat, filename), template.format(pkg=pkg)))

    written.append(write(os.path.join(plat, "SecurityConfig.java"),
                         ts.SECURITY_CONFIG.format(pkg=pkg, service=service,
                                                   public_matchers=PUBLIC_MATCHERS.get(service, ""))))
    written.append(write(os.path.join(plat, "OpenApiConfig.java"),
                         ts.OPENAPI_CONFIG.format(pkg=pkg, service=service, desc=desc)))

    # per-entity layers
    for name, table, fields in entities:
        written.append(write(os.path.join(src, "domain", name + ".java"),
                             ge.entity_source(pkg, name, table, fields)))
        written.append(write(os.path.join(src, "repo", name + "Repository.java"),
                             ge.repository_source(pkg, name, fields)))
        written.append(write(os.path.join(src, "web/dto", name + "Response.java"),
                             ge.response_dto_source(pkg, name, fields)))
        written.append(write(os.path.join(src, "web/dto", name + "CreateRequest.java"),
                             ge.request_dto_source(pkg, name, fields, "Create")))
        written.append(write(os.path.join(src, "web/dto", name + "UpdateRequest.java"),
                             ge.request_dto_source(pkg, name, fields, "Update")))
        written.append(write(os.path.join(src, "service", name + "Service.java"),
                             gl.service_source(pkg, name, table, fields, domain)))
        written.append(write(os.path.join(src, "web", name + "Controller.java"),
                             gl.controller_source(pkg, name, table, slug, desc)))
        written.append(write(os.path.join(test, "service", name + "ServiceTest.java"),
                             gl.service_test_source(pkg, name, fields)))

    return written


def apply_overlay():
    """Hand-written domain code lives in overlay/ and is copied over the generated
    tree, so regenerating scaffolding never destroys it."""
    source = os.path.join(ROOT, "overlay")
    if not os.path.isdir(source):
        return 0

    copied = 0
    for service in sorted(os.listdir(source)):
        src_dir = os.path.join(source, service)
        dst_dir = os.path.join(ROOT, "services", service)
        if not os.path.isdir(src_dir) or not os.path.isdir(dst_dir):
            continue
        for root, _dirs, files in os.walk(src_dir):
            for filename in files:
                src_file = os.path.join(root, filename)
                rel = os.path.relpath(src_file, src_dir)
                dst_file = os.path.join(dst_dir, rel)
                os.makedirs(os.path.dirname(dst_file), exist_ok=True)
                shutil.copy2(src_file, dst_file)
                copied += 1
    return copied


def main():
    target = os.path.join(ROOT, "services")
    if os.path.isdir(target):
        shutil.rmtree(target, ignore_errors=True)

    total = 0
    for spec in SERVICES:
        files = generate_service(spec)
        total += len(files)
        print(f"  {spec['name']:<28} port {spec['port']}  {len(spec['entities'])} entities"
              f"  {len(files)} files")
    overlaid = apply_overlay()
    print(f"\n{len(SERVICES)} services, {total} files generated into services/")
    if overlaid:
        print(f"{overlaid} hand-written files applied from overlay/")


if __name__ == "__main__":
    main()
