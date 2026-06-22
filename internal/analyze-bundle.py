#!/usr/bin/env python3
"""Analyze a sonatypeBundle directory and report size breakdown."""

import os
import sys
from collections import defaultdict
from pathlib import Path


def human(size: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024:
            return f"{size:.1f} {unit}"
        size /= 1024
    return f"{size:.1f} TB"


def classify(name: str) -> str:
    if name.endswith("-javadoc.jar"):
        return "javadoc jar"
    if name.endswith("-sources.jar"):
        return "sources jar"
    if name.endswith(".klib"):
        return ".klib"
    if name.endswith(".aar"):
        return ".aar"
    if name.endswith(".jar"):
        return ".jar"
    if name.endswith(".pom"):
        return ".pom"
    if name.endswith(".module"):
        return ".module"
    if name.endswith(".asc"):
        return ".asc signature"
    for ext in (".sha512", ".sha256", ".sha1", ".md5"):
        if name.endswith(ext):
            return "checksum"
    return "other"


def main(bundle_dir: str) -> None:
    root = Path(bundle_dir)
    if not root.is_dir():
        print(f"error: {bundle_dir} is not a directory", file=sys.stderr)
        sys.exit(1)

    files: list[tuple[int, Path]] = []
    by_type: dict[str, int] = defaultdict(int)
    by_type_count: dict[str, int] = defaultdict(int)

    for path in root.rglob("*"):
        if not path.is_file():
            continue
        size = path.stat().st_size
        files.append((size, path))

        kind = classify(path.name)
        by_type[kind] += size
        by_type_count[kind] += 1

    total = sum(s for s, _ in files)
    print(f"Total: {human(total)}  ({len(files)} files)\n")

    # --- By file type ---
    print("By file type:")
    print(f"  {'Type':<20} {'Size':>10}  {'Count':>6}")
    print(f"  {'-'*20} {'-'*10}  {'-'*6}")
    for kind, size in sorted(by_type.items(), key=lambda x: -x[1]):
        print(f"  {kind:<20} {human(size):>10}  {by_type_count[kind]:>6}")

if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "build/sonatypeBundle"
    main(target)
