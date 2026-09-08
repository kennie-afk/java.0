#!/usr/bin/env python3
"""Emits the SmartSeason Java microservices tree from the catalogue."""
import os
import pathlib
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_web
from catalogue import SERVICES
from gen_support import parse_entity, pkg_of, kebab_to_pascal
import gen_entity as ge
import strip_comments as sc
import gen_layers as gl
import tpl_platform as tp
import tpl_common as tc
import tpl_security as ts
import tpl_ratelimit as trl
import tpl_cache as tca

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

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
    # A Flyway migration is a historical record, not source code. Once it has run against
    # any database its bytes are fixed forever: Flyway checksums the file, and a rewrite —
    # even one that only removes a comment — makes every database that already ran it
    # refuse to start with "Migration checksum mismatch". That is not hypothetical. The
    # sibling SmartRE project was taken down by exactly this: thirteen applied migrations
    # had their comments stripped, and the failure surfaced at deploy time on the machines
    # that already held the data.
    #
    # So the generator scaffolds a migration once and never touches it again. Schema
    # changes are new migrations, which is how Flyway is meant to be used anyway.
    if "db/migration" in path.replace(os.sep, "/") and os.path.exists(path):
        return path

    if path.endswith(".java"):
        content = sc.strip_java(content)
    elif os.path.basename(path) == "Dockerfile":
        content = sc.strip_hash(content)
    # SQL comments are deliberately kept. They cost nothing at runtime, a migration is the
    # one place where "why" cannot be recovered from the code around it, and stripping
    # them is what caused the outage described above.

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

    plat = os.path.join(src, "platform")
    for filename, template in [
        ("BaseEntity.java", tc.BASE_ENTITY), ("TenantContext.java", tc.TENANT_CONTEXT),
        ("TenantMissingException.java", tc.TENANT_MISSING),
        ("ResourceNotFoundException.java", tc.NOT_FOUND),
        ("ConflictException.java", tc.CONFLICT),
        ("DomainRuleException.java", tc.VALIDATION_EX),
        ("ApiExceptionHandler.java", tc.EXCEPTION_HANDLER),
        ("JwtAuthenticationFilter.java", ts.JWT_FILTER),
        ("PageResponse.java", tca.PAGE_RESPONSE),
        ("DomainEvent.java", ts.DOMAIN_EVENT),
        ("EventPublisher.java", ts.EVENT_PUBLISHER),
        ("OutboxEntry.java", tc.OUTBOX_ENTRY),
        ("OutboxRepository.java", tc.OUTBOX_REPOSITORY),
        ("OutboxRelay.java", tc.OUTBOX_RELAY),
    ]:
        written.append(write(os.path.join(plat, filename), template.format(pkg=pkg)))

    # Rate limiting: one shared allowance per caller per service, held in Redis.
    rl = os.path.join(src, "ratelimit")
    for filename, template in [
        ("RateLimitProperties.java", trl.PROPERTIES),
        ("RateLimitDecision.java", trl.DECISION),
        ("TokenBucketLimiter.java", trl.LIMITER),
    ]:
        written.append(write(os.path.join(rl, filename), template.format(pkg=pkg)))
    written.append(write(os.path.join(rl, "RateLimitFilter.java"),
                         trl.FILTER.format(pkg=pkg, service=service)))
    written.append(write(os.path.join(res, "scripts/token-bucket.lua"), trl.TOKEN_BUCKET_LUA))

    written.append(write(os.path.join(plat, "CountCache.java"),
                         tca.COUNT_CACHE.format(pkg=pkg, service=service)))

    written.append(write(os.path.join(plat, "SecurityConfig.java"),
                         ts.SECURITY_CONFIG.format(pkg=pkg, service=service,
                                                   public_matchers=PUBLIC_MATCHERS.get(service, ""))))
    written.append(write(os.path.join(plat, "OpenApiConfig.java"),
                         ts.OPENAPI_CONFIG.format(pkg=pkg, service=service, desc=desc)))

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

def preserve_migrations(target):
    """Lifts every existing migration out of the way before the tree is wiped.

    Regeneration deletes services/ wholesale, which is the right behaviour for code —
    it guarantees no orphaned file survives a rename. It is the wrong behaviour for
    migrations: those are a historical record, and Flyway checksums them. Rewriting one
    that has already run makes every database holding that schema refuse to start with
    "Migration checksum mismatch", discovered at deploy time on the machine that already
    has the data. The sibling SmartRE project was taken down by exactly that.

    So migrations are carried across the wipe and restored afterwards. A generated
    migration only ever fills a gap where no file existed.
    """
    saved = {}
    for path in pathlib.Path(target).rglob("src/main/resources/db/migration/*.sql"):
        # Source only. target/ holds Maven's copies, which are recreated by every build
        # and are owned by root when the build ran in Docker — reading them is pointless
        # and writing them back fails.
        saved[str(path.relative_to(target))] = path.read_bytes()
    return saved

def restore_migrations(target, saved):
    for rel, body in saved.items():
        dest = os.path.join(target, rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as handle:
            handle.write(body)
    return len(saved)

def main():
    target = os.path.join(ROOT, "services")
    saved_migrations = {}
    if os.path.isdir(target):
        saved_migrations = preserve_migrations(target)
        shutil.rmtree(target, ignore_errors=True)

    total = 0
    for spec in SERVICES:
        files = generate_service(spec)
        total += len(files)
        print(f"  {spec['name']:<28} port {spec['port']}  {len(spec['entities'])} entities"
              f"  {len(files)} files")
    restored = restore_migrations(target, saved_migrations)
    overlaid = apply_overlay()
    print(f"\n{len(SERVICES)} services, {total} files generated into services/")
    if restored:
        print(f"{restored} existing migrations preserved unchanged across the regeneration")
    if overlaid:
        print(f"{overlaid} hand-written files applied from overlay/")

    # The web catalogue is generated from the same catalogue.py and rbac.py, so
    # it must be refreshed in the same breath. Regenerating only the Java leaves
    # the navigation offering services the controllers now refuse.
    gen_web.main()

if __name__ == "__main__":
    main()
