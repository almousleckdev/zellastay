import re
from pathlib import Path

from .model import Violation, code_lines, relative

IMPORT = re.compile(r"^\s*(?:import|export)\s+['\"]([^'\"]+)['\"]")
UI_TOOLKIT = re.compile(r"package:flutter/(?:src/)?(material|cupertino)(?:\.dart|/)")
PLATFORM_CHECK = re.compile(r"\bPlatform\.is(?:IOS|Android)\b|\bdefaultTargetPlatform\b|\bTargetPlatform\.")
NETWORK_EXCEPTION = re.compile(r"\b(DioException|SocketException|TimeoutException|HttpException|ClientException)\b")
DYNAMIC = re.compile(r"\bdynamic\b")
INTERNAL_ROOTS = ("features/", "core/", "shared/", "app/")
DOMAIN_FORBIDDEN = ("package:flutter/", "package:flutter_riverpod", "package:riverpod", "package:dio/", "package:http/")
PLATFORM_UI = "core/platform_ui/"
NETWORK_DIR = "core/network/"


def lib_path(path: Path, root: Path) -> str | None:
    parts = path.relative_to(root).parts
    if "lib" not in parts:
        return None
    return "/".join(parts[parts.index("lib") + 1:])


def resolve_internal(spec: str, file_lib_path: str) -> str | None:
    if spec.startswith("package:"):
        inner = spec.split("/", 1)[1] if "/" in spec else ""
        return inner if inner.startswith(INTERNAL_ROOTS) else None
    if spec.startswith("."):
        base = file_lib_path.split("/")[:-1]
        for segment in spec.split("/"):
            if segment == "..":
                if base:
                    base.pop()
            elif segment != ".":
                base.append(segment)
        return "/".join(base)
    return None


def layer_of(lib: str) -> tuple[str, str] | None:
    parts = lib.split("/")
    if len(parts) >= 4 and parts[0] == "features" and parts[2] in {"domain", "data", "presentation", "di"}:
        return parts[1], parts[2]
    return None


def check_dart(path: Path, root: Path, text: str) -> list[Violation]:
    if path.suffix != ".dart":
        return []
    lib = lib_path(path, root)
    if lib is None:
        return []
    rel = relative(path, root)
    found: list[Violation] = []
    in_platform_ui = lib.startswith(PLATFORM_UI)
    for number, line in code_lines(text):
        found.extend(check_line(rel, number, line, lib, in_platform_ui))
    return found


def check_line(rel: str, number: int, line: str, lib: str, in_platform_ui: bool) -> list[Violation]:
    found: list[Violation] = []

    def add(rule: str, message: str) -> None:
        found.append(Violation(rel, number, rule, message))

    if DYNAMIC.search(line):
        add("no-dynamic", "use precise types")
    if PLATFORM_CHECK.search(line) and not in_platform_ui:
        add("platform-abstraction", "platform branching belongs in core/platform_ui only")
    if NETWORK_EXCEPTION.search(line) and not lib.startswith(NETWORK_DIR):
        add("central-error-handling", "network exceptions are translated once in core/network")
    match = IMPORT.match(line)
    if not match:
        return found
    spec = match.group(1)
    toolkit = UI_TOOLKIT.search(spec)
    if toolkit:
        found.extend(check_toolkit(rel, number, toolkit.group(1), lib, in_platform_ui))
    if re.search(r"package:(dio|http)/", spec) and not (lib.startswith(NETWORK_DIR) or "/data/" in f"/{lib}"):
        add("central-error-handling", "HTTP clients are allowed only in core/network or a feature data layer")
    found.extend(check_layers(rel, number, spec, lib))
    return found


def check_toolkit(rel: str, number: int, toolkit: str, lib: str, in_platform_ui: bool) -> list[Violation]:
    def violation(message: str) -> list[Violation]:
        return [Violation(rel, number, "native-platform-ui", message)]

    if not in_platform_ui:
        return violation(f"{toolkit} widgets may only be imported inside core/platform_ui")
    if toolkit == "material" and "/ios/" in f"/{lib}":
        return violation("Material imports are forbidden in the iOS implementation")
    if toolkit == "cupertino" and "/android/" in f"/{lib}":
        return violation("Cupertino imports are forbidden in the Android implementation")
    return []


def check_layers(rel: str, number: int, spec: str, lib: str) -> list[Violation]:
    found: list[Violation] = []
    own = layer_of(lib)
    target = resolve_internal(spec, lib)
    target_layer = layer_of(target) if target else None

    def add(rule: str, message: str) -> None:
        found.append(Violation(rel, number, rule, message))

    if own and own[1] == "domain" and spec.startswith(DOMAIN_FORBIDDEN):
        add("clean-architecture", "domain must be pure Dart (no Flutter, Riverpod or HTTP clients)")
    if own and target_layer:
        feature, layer = own
        if target_layer[0] != feature:
            add("no-feature-coupling", f"feature '{feature}' must not import feature '{target_layer[0]}'; use shared/")
        if layer == "domain" and target_layer[1] in {"data", "presentation", "di"}:
            add("clean-architecture", "domain must not depend on data, presentation or di")
        if layer == "data" and target_layer[1] in {"presentation", "di"}:
            add("clean-architecture", "data must not depend on presentation or di")
        if layer == "presentation" and target_layer[1] == "data":
            add("clean-architecture", "presentation reaches data only through domain and di providers")
    if (lib.startswith(("core/", "shared/"))) and target and target.startswith("features/"):
        add("no-feature-coupling", "core and shared must not depend on features")
    return found
