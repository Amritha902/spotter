#!/usr/bin/env python3
"""Fail the build when production code declares something no screen can reach.

This exists because the same defect kept recurring. Six times in a sibling project a layer looked
finished — written, sensibly named, sometimes with tests — and no screen ever called it: the session
log, the spotlight, the talk-track editor, the follow-up list, a page-geometry helper, and
`DocumentRepository.delete`. Each was found by hand, late, and only because someone went looking.
It is here from day one rather than after the same six mistakes.

"It's built" and "it's reachable" are different claims, and building bottom-up makes the second one
easy to assume. A person auditing for this once catches one. Doing it on every build catches all of
them, which is the difference between a habit and a guarantee.

The check is deliberately blunt: every function declared under the main source set must be
referenced somewhere other than its own declaration, unless it is an entry point the framework calls
for us. Blunt is the point — a clever version that inferred call graphs would have its own blind
spots, and this one is easy to reason about when it fires.

Run directly, or via `gradle checkReachable`.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

MAIN = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "java"

# `[^\S\n]*` rather than `\s*` for the leading indent, and it matters: `\s` matches newlines, so a
# declaration preceded by a blank line matched from the *previous* line. That shifted the computed
# line number by one, the `override` test then read the wrong line, and framework callbacks were
# reported as unreachable. Two false positives is all it takes for a check to start being ignored.
DECL = re.compile(r"^[^\S\n]*(?:@\w+(?:\([^)]*\))?[^\S\n]*)*"
                  r"(?:public\s+|private\s+|internal\s+|protected\s+)?"
                  r"(?:override\s+|open\s+|abstract\s+|inline\s+|suspend\s+|operator\s+)*"
                  r"fun\s+(?:<[^>]+>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\(", re.M)

# Called by the framework, not by us. Absence from our own code is correct for these.
ENTRY_POINTS = {
    "main",
    "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy",
    "onSensorChanged", "onAccuracyChanged",
    "onReceived", "onError", "onCompleted",
    "onStart_", "onDone",
}

# An override satisfies an interface the framework calls; that is its reachability.
OVERRIDE = re.compile(r"^[^\S\n]*(?:@\w+(?:\([^)]*\))?[^\S\n]*)*"
                      r"(?:private\s+|internal\s+|protected\s+)?override\s+")


def declarations() -> dict[str, list[tuple[Path, int]]]:
    found: dict[str, list[tuple[Path, int]]] = {}
    for path in sorted(MAIN.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        for match in DECL.finditer(text):
            name = match.group(1)
            line_no = text[: match.start()].count("\n")
            line = lines[line_no] if line_no < len(lines) else ""
            # Overrides and framework entry points are reachable by definition.
            if OVERRIDE.match(line) or name in ENTRY_POINTS:
                continue
            found.setdefault(name, []).append((path, line_no + 1))
    return found


def reference_count(name: str, sources: list[str]) -> int:
    """How many times the name appears at all, declarations included."""
    pattern = re.compile(rf"\b{re.escape(name)}\b")
    return sum(len(pattern.findall(src)) for src in sources)


def main() -> int:
    if not MAIN.is_dir():
        print(f"no source at {MAIN}", file=sys.stderr)
        return 2

    sources = [p.read_text(encoding="utf-8") for p in MAIN.rglob("*.kt")]
    unreachable: list[tuple[str, Path, int]] = []

    for name, sites in declarations().items():
        # One occurrence per declaration and nothing else means nobody calls it. A name declared
        # twice (an overload) needs at least as many references as declarations before it counts.
        if reference_count(name, sources) <= len(sites):
            for path, line in sites:
                unreachable.append((name, path, line))

    if not unreachable:
        print(f"reachability: OK — every function under {MAIN.name} is called from somewhere")
        return 0

    print("reachability: FAILED\n")
    print("These are declared in production code and called from nowhere in it.")
    print("Either wire them to a screen or delete them — a layer that no screen reaches is")
    print("not a feature, however finished it looks.\n")
    for name, path, line in sorted(unreachable, key=lambda x: str(x[1])):
        print(f"  {path.relative_to(MAIN.parent.parent.parent.parent)}:{line}  fun {name}")
    print(f"\n{len(unreachable)} unreachable declaration(s).")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
