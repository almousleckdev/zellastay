import re
from pathlib import Path

from .files import project_files

EXEMPT = {"CLAUDE.md", "attribution.py"}
PATTERNS = [
    re.compile(r"co-authored-by", re.I),
    re.compile(r"generated with \[?claude", re.I),
    re.compile(r"noreply@anthropic\.com", re.I),
    re.compile("\U0001F916"),
]
TEXT_SUFFIXES = {".md", ".java", ".kts", ".yaml", ".yml", ".sql", ".sh", ".py", ".toml", ".properties", ".json", ".ts", ".tsx", ".dart"}


def check(root: Path):
    errors = []
    for path in project_files(root):
        if path.suffix not in TEXT_SUFFIXES or path.name in EXEMPT:
            continue
        text = path.read_text(errors="ignore")
        for pattern in PATTERNS:
            if pattern.search(text):
                errors.append(f"AI attribution pattern {pattern.pattern!r} in {path.relative_to(root)}")
    return errors
