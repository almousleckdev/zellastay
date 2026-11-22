#!/usr/bin/env python3
"""Fails when a source file exceeds the repository line limit (ADR-43).

Lines are physical lines: blank lines and comments count, so the limit cannot be met by
compressing code. Limit, roots, extensions and exclusions live in file-size.json.
"""
import argparse
import json
import os
import sys
from pathlib import Path

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG = Path(__file__).resolve().parent / "file-size.json"


def line_count(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    if not text:
        return 0
    return text.count("\n") + (0 if text.endswith("\n") else 1)


def is_checked(path, config):
    if path.name.endswith(tuple(config["excludeSuffixes"])):
        return False
    return path.suffix in config["extensions"]


def find_violations(root, config):
    excluded = set(config["excludeDirs"])
    violations = []
    for source_root in config["roots"]:
        base = root / source_root
        if not base.is_dir():
            continue
        for directory, subdirs, files in os.walk(base):
            subdirs[:] = [name for name in subdirs if name not in excluded]
            for name in files:
                path = Path(directory) / name
                if not is_checked(path, config):
                    continue
                lines = line_count(path)
                if lines > config["maxLines"]:
                    violations.append((path.relative_to(root).as_posix(), lines))
    return sorted(violations)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    args = parser.parse_args(argv)
    config = json.loads(args.config.read_text())
    violations = find_violations(args.root, config)
    for path, lines in violations:
        print(f"{path}: {lines} lines (limit {config['maxLines']})")
    if violations:
        print(f"file size check: {len(violations)} file(s) over the limit", file=sys.stderr)
        return 1
    print(f"file size check: 0 problem(s), limit {config['maxLines']} lines")
    return 0


if __name__ == "__main__":
    sys.exit(main())
