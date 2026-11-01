#!/usr/bin/env python3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from standards.common import check_common
from standards.dart import check_dart
from standards.model import is_checked
from standards.typescript import check_typescript

ROOTS = ("apps", "packages")


def scan(root: Path) -> list:
    violations = []
    for name in ROOTS:
        base = root / name
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if not path.is_file() or not is_checked(path.relative_to(root)):
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            violations.extend(check_common(path, root, text))
            violations.extend(check_typescript(path, root, text))
            violations.extend(check_dart(path, root, text))
    return violations


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path(__file__).resolve().parents[2]
    violations = scan(root)
    for violation in violations:
        print(violation)
    scanned = [name for name in ROOTS if (root / name).is_dir()]
    print(f"standards check: {len(violations)} violation(s); scanned {scanned or 'nothing (no apps/packages yet)'}")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
