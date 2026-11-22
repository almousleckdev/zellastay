import re
from pathlib import Path

from .files import markdown_files

REGISTERS = (
    ("open decision", r"OD-\d+[a-z]?", "docs/OPEN_DECISIONS.md"),
    ("ADR", r"ADR-\d+", "docs/DECISIONS.md"),
)


def check(root: Path):
    docs = "\n".join(p.read_text() for p in markdown_files(root))
    errors = []
    for label, pattern, register in REGISTERS:
        register_text = (root / register).read_text()
        for ref in sorted(set(re.findall(pattern, docs))):
            if not re.search(rf"\| {ref} \|", register_text):
                errors.append(f"undefined {label} {ref}")
    return errors
