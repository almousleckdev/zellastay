from pathlib import Path

SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".next", "dist", "coverage", "__pycache__"}


def project_files(root: Path):
    for path in root.rglob("*"):
        if path.is_file() and not (set(path.relative_to(root).parts) & SKIP_DIRS):
            yield path


def markdown_files(root: Path):
    return [p for p in project_files(root) if p.suffix == ".md"]
