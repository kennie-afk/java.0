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
