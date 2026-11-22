import re
from pathlib import Path

from .files import markdown_files

LINK = re.compile(r"\]\(([^)#\s]+)(?:#[^)]*)?\)")
EXTERNAL = ("http://", "https://", "mailto:")


def check(root: Path):
    errors = []
    for md in markdown_files(root):
        for target in LINK.findall(md.read_text()):
            if target.startswith(EXTERNAL):
                continue
            if not (md.parent / target).resolve().exists():
                errors.append(f"broken link in {md.relative_to(root)}: {target}")
    return errors
