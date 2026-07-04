#!/usr/bin/env python3
"""Insert #![allow(clippy::unwrap_used, clippy::expect_used)] into #[cfg(test)] modules."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ALLOW = "#![allow(clippy::unwrap_used, clippy::expect_used)]"

CRATES = [
    "crates/mycelium-node/src",
    "crates/mycelium-coin/src",
    "crates/mycelium-core/src",
    "crates/mycelium-miniapp/src",
]

PATTERNS = [
    re.compile(r"(#\[cfg\(test\)\]\s*\n(?:#\[path[^\]]+\]\s*\n)?mod\s+\w+\s*\{)"),
    re.compile(r"(#\[cfg\(test\)\]\s*\nmod\s+\w+\s*\{)"),
]


def patch_file(path: Path) -> bool:
    text = path.read_text()
    if ALLOW in text:
        return False
    changed = False
    for pat in PATTERNS:
        def repl(m: re.Match[str]) -> str:
            nonlocal changed
            block = m.group(1)
            if ALLOW in block:
                return block
            changed = True
            return block + "\n    " + ALLOW + "\n"

        text = pat.sub(repl, text)
    if changed:
        path.write_text(text)
    return changed


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    n = 0
    for rel in CRATES:
        base = root / rel
        for path in sorted(base.rglob("*.rs")):
            if patch_file(path):
                print(f"patched {path.relative_to(root)}")
                n += 1
    print(f"done: {n} file(s) patched")
    return 0


if __name__ == "__main__":
    sys.exit(main())
