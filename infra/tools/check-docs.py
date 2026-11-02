#!/usr/bin/env python3
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".next", "dist", "coverage"}
ATTRIBUTION_EXEMPT = {"CLAUDE.md", "check-docs.py"}
ATTRIBUTION_PATTERNS = [
    re.compile(r"co-authored-by", re.I),
    re.compile(r"generated with \[?claude", re.I),
    re.compile(r"noreply@anthropic\.com", re.I),
    re.compile("\U0001F916"),
]
TEXT_SUFFIXES = {".md", ".java", ".kts", ".yaml", ".yml", ".sql", ".sh", ".py", ".toml", ".properties", ".json", ".ts", ".tsx", ".dart"}


def project_files():
    for path in ROOT.rglob("*"):
        if path.is_file() and not (set(path.relative_to(ROOT).parts) & SKIP_DIRS):
            yield path


def markdown_files():
    return [p for p in project_files() if p.suffix == ".md"]


def check_links(errors):
    for md in markdown_files():
        for target in re.findall(r"\]\(([^)#\s]+)(?:#[^)]*)?\)", md.read_text()):
            if target.startswith(("http://", "https://", "mailto:")):
                continue
            if not (md.parent / target).resolve().exists():
                errors.append(f"broken link in {md.relative_to(ROOT)}: {target}")


def check_references(errors):
    docs = "\n".join(p.read_text() for p in markdown_files())
    open_decisions = (ROOT / "docs/OPEN_DECISIONS.md").read_text()
    decisions = (ROOT / "docs/DECISIONS.md").read_text()
    for ref in sorted(set(re.findall(r"OD-\d+[a-z]?", docs))):
        if not re.search(rf"\| {ref} \|", open_decisions):
            errors.append(f"undefined open decision {ref}")
    for ref in sorted(set(re.findall(r"ADR-\d+", docs))):
        if not re.search(rf"\| {ref} \|", decisions):
            errors.append(f"undefined ADR {ref}")


def check_attribution(errors):
    for path in project_files():
        if path.suffix not in TEXT_SUFFIXES or path.name in ATTRIBUTION_EXEMPT:
            continue
        text = path.read_text(errors="ignore")
        for pattern in ATTRIBUTION_PATTERNS:
            if pattern.search(text):
                errors.append(f"AI attribution pattern {pattern.pattern!r} in {path.relative_to(ROOT)}")


def main():
    errors = []
    check_links(errors)
    check_references(errors)
    check_attribution(errors)
    for error in errors:
        print(error)
    print(f"docs check: {len(errors)} problem(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
