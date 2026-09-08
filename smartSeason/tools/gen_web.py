#!/usr/bin/env python3
"""Generates the web application's screen catalogue from the service catalogue.

The backend is generated from `catalogue.py`; so is the UI that talks to it.
This writes one TypeScript module describing every service, every entity, the
columns worth showing in a list and the fields a create form needs. The screens
themselves are a single generic route that renders from this description, so
adding an entity to the catalogue gives it a working screen with no new page.

Run `python3 tools/gen_web.py` after changing the catalogue.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import rbac
from catalogue import SERVICES
from gen_support import parse_entity

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "apps/web/src/lib/catalogue.generated.ts")

# Fields that carry a credential. These are never listed and never rendered in a
# form. Content hashes (recordHash, chainHash, perceptualHash) are deliberately
# NOT here - an audit trail whose hashes are hidden is useless.
SECRET_FIELDS = {"passwordHash", "tokenHash", "codeHash"}
SECRET_PATTERN = re.compile(r"secret|apikey|privatekey|passphrase", re.I)

# Groups in the order they appear in the rail, with the icon each one uses.
GROUP_META = [
    ("farm", "Farm", "farms"),
    ("iot", "Devices", "devices"),
    ("workforce", "Workforce", "workforce"),
    ("market", "Market", "marketplace"),
    ("money", "Money", "money"),
    ("platform", "Platform", "platform"),
    ("identity", "Identity", "identity"),
]

# A list's first column should say what the row *is*. These names are checked in
# order; the first one an entity has becomes its lead column.
LABEL_FIELDS = [
    "name", "title", "fullName", "commonName", "caseNumber", "code",
    "email", "reference", "stageName", "templateName", "plateNumber",
]

MAX_COLUMNS = 6


def humanise(name):
    """fullName -> Full name ; expectedYieldKg -> Expected yield kg"""
    spaced = re.sub(r"(?<!^)(?=[A-Z])", " ", name)
    spaced = spaced.replace("_", " ").strip().lower()
    return spaced[0].upper() + spaced[1:] if spaced else name


def table_label(table):
    """supply_listings -> Supply listings"""
    words = table.replace("_", " ")
    return words[0].upper() + words[1:]


def entity_label(entity_name):
    """SupplyListing -> Supply listing"""
    spaced = re.sub(r"(?<!^)(?=[A-Z])", " ", entity_name).lower()
    return spaced[0].upper() + spaced[1:]


def is_secret(field):
    return field.name in SECRET_FIELDS or bool(SECRET_PATTERN.search(field.name))


def is_status(field):
    return field.kind == "enum" and field.name in ("status", "state")


def numeric(field):
    return field.kind in ("int", "long", "decimal")


def column_spec(field):
    spec = {"name": field.name, "label": humanise(field.name), "kind": field.kind}
    if field.kind == "enum":
        spec["badge"] = True
    if numeric(field):
        spec["numeric"] = True
    return spec


def pick_columns(fields):
    """Chooses the handful of fields that make a scannable list row.

    Long text, JSON blobs and opaque foreign keys are left out: they make a
    table unreadable and say nothing at a glance.
    """
    usable = [
        f for f in fields
        if not is_secret(f) and f.kind not in ("text", "json", "uuid")
    ]
    if not usable:
        # Everything worth showing was filtered out (a pure join table, say), so
        # fall back to whatever non-secret fields exist.
        usable = [f for f in fields if not is_secret(f)]

    lead = None
    for preferred in LABEL_FIELDS:
        for field in usable:
            if field.name == preferred:
                lead = field
                break
        if lead:
            break
    if lead is None:
        strings = [f for f in usable if f.kind == "string"]
        lead = strings[0] if strings else usable[0]

    status = next((f for f in usable if is_status(f) and f is not lead), None)
    middle = [f for f in usable if f is not lead and f is not status]

    room = MAX_COLUMNS - 1 - (1 if status else 0)
    chosen = [lead] + middle[:room] + ([status] if status else [])
    return [column_spec(f) for f in chosen]


# Fields that should be picked from the Kenyan administrative list rather than
# typed freehand. subCounty depends on the county chosen alongside it.
LOOKUPS = {"county": "county", "subCounty": "subCounty"}

# Fields that name a person in this organisation. Rendered as a picker of team
# members rather than a UUID box - a worker record that is not linked to an
# account cannot sign in and see its own tasks, so this link matters.
USER_LOOKUPS = {"userId", "ownerUserId", "supervisorId", "assignedTo"}


def form_spec(field):
    spec = {
        "name": field.name,
        "label": humanise(field.name),
        "kind": field.kind,
        "required": bool(field.notnull),
    }
    if field.enum_values:
        spec["options"] = list(field.enum_values)
    if field.name in LOOKUPS and field.kind == "string":
        spec["lookup"] = LOOKUPS[field.name]
    if field.name in USER_LOOKUPS and field.kind == "uuid":
        spec["lookup"] = "user"
    return spec


def pick_form_fields(fields):
    """Every field a caller may legitimately set, required ones first."""
    usable = [f for f in fields if not is_secret(f)]
    required = [f for f in usable if f.notnull]
    optional = [f for f in usable if not f.notnull]
    return [form_spec(f) for f in required + optional]


def build():
    by_group = {}
    for spec in SERVICES:
        slug = spec["name"][:-len("-service")] if spec["name"].endswith("-service") else spec["name"]
        entities = []
        for entity_name, table, fields in (parse_entity(e) for e in spec["entities"]):
            entities.append({
                "slug": table.replace("_", "-"),
                "label": table_label(table),
                "singular": entity_label(entity_name),
                "path": f"/api/{slug}/v1/{table.replace('_', '-')}",
                "columns": pick_columns(fields),
                "formFields": pick_form_fields(fields),
            })
        by_group.setdefault(spec["group"], []).append({
            "slug": slug,
            "label": humanise(slug.replace("-", " ")),
            "description": spec["desc"],
            "entities": entities,
            # The same table the controllers are annotated from, so the menu can
            # never offer something the service will refuse.
            "access": {role: rbac.level(slug, role) for role in rbac.ROLES
                       if rbac.level(slug, role) != rbac.NONE},
        })

    groups = []
    for group_slug, label, icon in GROUP_META:
        services = by_group.get(group_slug, [])
        if not services:
            continue
        groups.append({
            "slug": group_slug,
            "label": label,
            "icon": icon,
            "services": services,
        })

    missing = set(by_group) - {g for g, _, _ in GROUP_META}
    if missing:
        raise SystemExit(f"catalogue has groups with no rail entry: {sorted(missing)}")

    return groups


HEADER = """// GENERATED by tools/gen_web.py from tools/catalogue.py - do not edit by hand.
// Run `python3 tools/gen_web.py` after changing the service catalogue.

export type FieldKind =
  | "uuid" | "string" | "text" | "int" | "long"
  | "decimal" | "bool" | "ts" | "date" | "json" | "enum";

export interface ColumnSpec {
  name: string;
  label: string;
  kind: FieldKind;
  badge?: boolean;
  numeric?: boolean;
}

export interface FormFieldSpec {
  name: string;
  label: string;
  kind: FieldKind;
  required: boolean;
  options?: string[];
  /**
   * Rendered from a list rather than typed freehand: the Kenyan administrative
   * areas, or the people in this organisation.
   */
  lookup?: "county" | "subCounty" | "user";
}

export interface EntitySpec {
  slug: string;
  label: string;
  singular: string;
  path: string;
  columns: ColumnSpec[];
  formFields: FormFieldSpec[];
}

export type AccessLevel = "OWN" | "READ" | "WRITE" | "FULL";

export interface ServiceSpec {
  slug: string;
  label: string;
  description: string;
  entities: EntitySpec[];
  /** Role to the level it holds here. A role absent from the map has none. */
  access: Partial<Record<Role, AccessLevel>>;
}

export interface GroupSpec {
  slug: string;
  label: string;
  icon: string;
  services: ServiceSpec[];
}

export const ROLES = [
  {roles}
] as const;

export type Role = (typeof ROLES)[number];

export const ROLE_LABELS: Record<Role, string> = {role_labels};

/** Features that are not one service's CRUD. */
export const FEATURE_ROLES: Record<string, Role[]> = {features};

export const GROUPS: GroupSpec[] = """

FOOTER = """;

export const SERVICES: ServiceSpec[] = GROUPS.flatMap((group) => group.services);

export function findService(slug: string): ServiceSpec | undefined {
  return SERVICES.find((service) => service.slug === slug);
}

export function findEntity(
  serviceSlug: string,
  entitySlug: string
): { service: ServiceSpec; entity: EntitySpec } | undefined {
  const service = findService(serviceSlug);
  const entity = service?.entities.find((item) => item.slug === entitySlug);
  return service && entity ? { service, entity } : undefined;
}

export function groupOf(serviceSlug: string): GroupSpec | undefined {
  return GROUPS.find((group) => group.services.some((s) => s.slug === serviceSlug));
}

const RANK: Record<AccessLevel, number> = { OWN: 1, READ: 2, WRITE: 3, FULL: 4 };

/** The level a role holds on a service. ADMIN always holds FULL. */
export function accessLevel(role: Role, serviceSlug: string): AccessLevel | null {
  if (role === "ADMIN") return "FULL";
  return findService(serviceSlug)?.access[role] ?? null;
}

export function canRead(role: Role, serviceSlug: string): boolean {
  return accessLevel(role, serviceSlug) !== null;
}

export function canWrite(role: Role, serviceSlug: string): boolean {
  const level = accessLevel(role, serviceSlug);
  return level !== null && RANK[level] >= RANK.WRITE;
}

export function canDelete(role: Role, serviceSlug: string): boolean {
  return accessLevel(role, serviceSlug) === "FULL";
}

export function canUseFeature(role: Role, feature: string): boolean {
  return (FEATURE_ROLES[feature] ?? []).includes(role);
}
"""


def main():
    groups = build()
    header = (HEADER
              .replace("{roles}", ",\n  ".join(f'"{role}"' for role in rbac.ROLES))
              .replace("{role_labels}", json.dumps(rbac.ROLE_LABELS, indent=2))
              .replace("{features}", json.dumps(rbac.FEATURES, indent=2)))
    body = json.dumps(groups, indent=2)
    with open(OUT, "w") as handle:
        handle.write(header + body + FOOTER)

    services = sum(len(g["services"]) for g in groups)
    entities = sum(len(s["entities"]) for g in groups for s in g["services"])
    print(f"web catalogue written: {len(groups)} groups, {services} services, {entities} entities")


if __name__ == "__main__":
    main()
