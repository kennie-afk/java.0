#!/usr/bin/env python3
"""Fails if a "use server" module exports anything but an async function.

Next.js enforces this at runtime, not at compile time, so `tsc` passes and the
page 500s in the browser instead. This catches it before that.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "apps" / "web" / "src"
EXPORT = re.compile(r"^export\s+(\w+)", re.M)

problems = []
for path in sorted(ROOT.rglob("*.ts")) + sorted(ROOT.rglob("*.tsx")):
    text = path.read_text()
    first = text.lstrip().split("\n", 1)[0].strip().rstrip(";").strip('"').strip("'")
    if first != "use server":
        continue
    for match in EXPORT.finditer(text):
        keyword = match.group(1)
        # `export type` and `export interface` are erased at build time.
        if keyword in ("type", "interface"):
            continue
        if keyword == "async":
            continue
        line = text[: match.start()].count("\n") + 1
        problems.append(f"{path.relative_to(ROOT.parents[2])}:{line}: exports '{keyword}'")

if problems:
    print('"use server" modules may export only async functions:')
    print("\n".join(f"  {problem}" for problem in problems))
    sys.exit(1)
print('every "use server" module exports only async functions')
