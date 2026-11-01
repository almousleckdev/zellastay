import re
from pathlib import Path

from .model import MAX_LINES, Violation, code_lines, is_checked, relative

SECRET_PATTERN = re.compile(
    r"CLOUDINARY_API_SECRET|CLOUDINARY_URL|\bapi_secret\b|NEXT_PUBLIC_\w*(SECRET|PRIVATE|API_KEY|TOKEN)\w*"
)
SECRET_SCANNED_SUFFIXES = {".ts", ".tsx", ".js", ".mjs", ".json", ".dart", ".yaml", ".yml", ".env"}
SOURCE_SUFFIXES = {".ts", ".tsx", ".dart"}


def check_file_size(path: Path, root: Path, text: str) -> list[Violation]:
    if path.suffix not in SOURCE_SUFFIXES:
        return []
    count = len(text.splitlines())
    if count <= MAX_LINES:
        return []
    return [Violation(relative(path, root), count, "file-size",
                      f"{count} lines exceeds {MAX_LINES}; split by responsibility")]


def check_secrets(path: Path, root: Path, text: str) -> list[Violation]:
    name = path.name
    if name.startswith(".env") and not name.endswith(".example"):
        return [Violation(relative(path, root), 1, "no-env-files", "environment files must not live in apps/packages")]
    if path.suffix not in SECRET_SCANNED_SUFFIXES:
        return []
    return [
        Violation(relative(path, root), number, "no-secrets-in-frontend",
                  "media/provider secrets and secret-like public variables are forbidden in frontend code")
        for number, line in code_lines(text)
        if SECRET_PATTERN.search(line)
    ]


def check_common(path: Path, root: Path, text: str) -> list[Violation]:
    if not is_checked(path.relative_to(root)):
        return []
    return check_file_size(path, root, text) + check_secrets(path, root, text)
