import re
from pathlib import Path

from .model import Violation, code_lines, relative

ANY_PATTERN = re.compile(r":\s*any\b|\bas any\b|<any>|\bany\[\]|Array<any>|Promise<any>|Record<[^>]*,\s*any>")
SUPPRESSION = re.compile(r"@ts-ignore|@ts-nocheck")
RAW_NETWORK = re.compile(r"(?<![\w.])fetch\(|from\s+['\"]axios['\"]|require\(['\"]axios['\"]\)")
IMPORT_SPECIFIER = re.compile(r"(?:from|import|require\()\s*['\"]([^'\"]+)['\"]")
TS_SUFFIXES = {".ts", ".tsx"}


def is_test(path: Path) -> bool:
    return ".test." in path.name or ".spec." in path.name


def app_of(path: Path, root: Path) -> str | None:
    parts = path.relative_to(root).parts
    return parts[1] if len(parts) > 2 and parts[0] == "apps" else None


def check_typescript(path: Path, root: Path, text: str) -> list[Violation]:
    if path.suffix not in TS_SUFFIXES:
        return []
    rel = relative(path, root)
    found: list[Violation] = []
    network_allowed = "/api-client/" in f"/{rel}" or is_test(path)
    for number, line in code_lines(text):
        if ANY_PATTERN.search(line):
            found.append(Violation(rel, number, "no-any", "use precise types or unknown with narrowing"))
        if SUPPRESSION.search(line):
            found.append(Violation(rel, number, "no-ts-suppression", "use @ts-expect-error with a reason"))
        if "dangerouslySetInnerHTML" in line:
            found.append(Violation(rel, number, "no-raw-html", "render sanitized content through the shared component"))
        if not network_allowed and RAW_NETWORK.search(line):
            found.append(Violation(rel, number, "central-api-client",
                                   "network calls belong in the shared api-client so error handling is not duplicated"))
    found.extend(check_cross_app_imports(path, root, text, rel))
    return found


def check_cross_app_imports(path: Path, root: Path, text: str, rel: str) -> list[Violation]:
    own = app_of(path, root)
    if own is None:
        return []
    found = []
    for number, line in code_lines(text):
        for spec in IMPORT_SPECIFIER.findall(line):
            target = None
            if spec.startswith("."):
                resolved = (path.parent / spec).resolve()
                try:
                    parts = resolved.relative_to(root.resolve()).parts
                except ValueError:
                    continue
                target = parts[1] if len(parts) > 1 and parts[0] == "apps" else None
            elif spec.startswith("apps/") or "/apps/" in spec:
                target = spec.split("apps/", 1)[1].split("/")[0]
            if target and target != own:
                found.append(Violation(rel, number, "no-cross-app-import",
                                       f"apps are independent; shared code goes to packages/ (imports apps/{target})"))
    return found
