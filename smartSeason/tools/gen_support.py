"""Naming + type mapping helpers for the SmartSeason code generator."""
import re

JAVA_TYPE = {
    "uuid": "UUID", "string": "String", "text": "String", "int": "Integer",
    "long": "Long", "decimal": "BigDecimal", "bool": "Boolean",
    "ts": "Instant", "date": "LocalDate", "json": "String",
}
SQL_TYPE = {
    "uuid": "UUID", "string": "VARCHAR(255)", "text": "TEXT", "int": "INTEGER",
    "long": "BIGINT", "decimal": "NUMERIC(18,4)", "bool": "BOOLEAN",
    "ts": "TIMESTAMPTZ", "date": "DATE", "json": "JSONB",
}

def snake(name):
    s = re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
    return re.sub(r"_+", "_", s)

def kebab_to_pascal(name):
    return "".join(p.capitalize() for p in name.split("-"))

def kebab_to_camel(name):
    p = kebab_to_pascal(name)
    return p[0].lower() + p[1:]

def pkg_of(service):
    """identity-service -> identity ; device-registry-service -> deviceregistry"""
    base = service[:-8] if service.endswith("-service") else service
    return base.replace("-", "")

def lower_first(s):
    return s[0].lower() + s[1:]

class Field:
    def __init__(self, spec):
        parts = spec.split(":")
        self.name = parts[0]
        raw = parts[1]
        flags = parts[2].split(",") if len(parts) > 2 else []
        self.notnull = "nn" in flags
        self.unique = "uq" in flags
        self.indexed = "ix" in flags or self.unique
        # "srv": built by the service, never accepted from the client (object-store keys).
        self.server = "srv" in flags
        self.enum_values = None
        if raw.startswith("enum("):
            self.kind = "enum"
            self.enum_values = raw[5:-1].split("|")
        else:
            self.kind = raw
        self.column = snake(self.name)

    @property
    def java_type(self):
        return self.enum_name if self.kind == "enum" else JAVA_TYPE[self.kind]

    @property
    def sql_type(self):
        return "VARCHAR(64)" if self.kind == "enum" else SQL_TYPE[self.kind]

    @property
    def enum_name(self):
        return self.name[0].upper() + self.name[1:]

    @property
    def getter(self):
        return "get" + self.name[0].upper() + self.name[1:]

    @property
    def setter(self):
        return "set" + self.name[0].upper() + self.name[1:]

    @property
    def is_text(self):
        return self.kind in ("text", "json")

def parse_entity(ent):
    name, table, raw_fields = ent
    return name, table, [Field(f) for f in raw_fields]


# References whose field name does not spell the target entity, scoped to the service they live
# in (the same field name can mean a same-service row in one service and another service's row
# elsewhere: `orderId` is a PurchaseOrder in order-service and a foreign id in payment-service).
REFERENCE_ALIASES = {
    ("automation", "ruleId"): "AutomationRule",
    ("task", "assignmentId"): "TaskAssignment",
    ("fraud", "caseId"): "FraudCase",
    ("order", "orderId"): "PurchaseOrder",
    ("logistics", "assignedVehicleId"): "Vehicle",
    ("ledger", "parentAccountId"): "Account",
    ("ledger", "reversalOfId"): "JournalEntry",
    ("payout", "batchId"): "PayoutBatch",
    ("media", "assetId"): "MediaAsset",
}


def reference_fields(fields, sibling_entities, own_name=None, pkg=None):
    """Fields that name another entity in the SAME service, as (field, EntityName) pairs.

    The catalogue has no explicit foreign keys, so the rule is the naming convention almost every
    entity already follows: a uuid field called `<entity>Id` points at that entity (`farmId` ->
    Farm, `plotId` -> Plot). The match is exact on purpose. A looser suffix match would read
    `workOrderId` in a service that has an `Order` entity as a reference to Order and reject
    valid values; a missed reference is merely unchecked, a wrong one breaks real requests. The
    handful of fields that do not spell their target are listed in REFERENCE_ALIASES.

    References to entities owned by another service (`ownerUserId`, `cooperativeId`) are not
    resolvable here, because the row lives in another service's database. They are listed in
    docs/TENANCY.md as unvalidated rather than silently assumed safe.
    """
    by_stem = {e[0].lower() + e[1:]: e for e in sibling_entities}
    pairs = []
    for f in fields:
        if f.kind != "uuid" or not f.name.endswith("Id") or len(f.name) <= 2:
            continue
        alias = REFERENCE_ALIASES.get((pkg, f.name))
        if alias and alias in sibling_entities:
            pairs.append((f, alias))
            continue
        stem = f.name[:-2]
        if stem in by_stem and by_stem[stem] != own_name:
            pairs.append((f, by_stem[stem]))
    return pairs
