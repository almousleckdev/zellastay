#!/usr/bin/env python3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from docs_checks import attribution, links, references  # noqa: E402

ROOT = HERE.parents[1]
CHECKS = (links.check, references.check, attribution.check)


def main():
    errors = [error for check in CHECKS for error in check(ROOT)]
    for error in errors:
        print(error)
    print(f"docs check: {len(errors)} problem(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
