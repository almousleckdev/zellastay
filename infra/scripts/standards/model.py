import re
from dataclasses import dataclass
from pathlib import Path

MAX_LINES = 300
SKIPPED_DIRECTORIES = {
    "node_modules", ".next", "build", ".dart_tool", "dist", "coverage", ".turbo", "generated",
}
GENERATED_SUFFIXES = (".g.dart", ".freezed.dart", ".gr.dart", ".d.ts", ".mocks.dart")
LINE_COMMENT = re.compile(r"^\s*(//|\*|/\*|#)")


@dataclass(frozen=True)
class Violation:
    path: str
    line: int
    rule: str
    message: str

    def __str__(self) -> str:
        return f"{self.path}:{self.line}: [{self.rule}] {self.message}"


def is_checked(path: Path) -> bool:
    if any(part in SKIPPED_DIRECTORIES for part in path.parts):
        return False
    return not path.name.endswith(GENERATED_SUFFIXES)


def code_lines(text: str):
    for number, line in enumerate(text.splitlines(), start=1):
        if not LINE_COMMENT.match(line):
            yield number, line


def relative(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()
