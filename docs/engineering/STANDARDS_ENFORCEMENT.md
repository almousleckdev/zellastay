# Standards Enforcement Matrix

Every mandatory engineering rule needs a mechanism that fails the build, or an explicit statement of what is still review-only. Status: **Active** (runs in CI today), **Ready** (written and tested, activates when the component exists), **Planned** (to be created with the component; the phase is stated), **Review** (cannot be automated; reviewer checklist).

## 1. Backend

| Rule | Mechanism | Status |
|---|---|---|
| Module boundaries and allowed dependencies equal the documented contract | Spring Modulith `verify()` + `ModuleStructureTest` (compares `package-info.java` with `MODULAR_ARCHITECTURE.md` §4) | Active |
| Layering (`web → application → domain ← infrastructure`), no framework in domain | ArchUnit `ArchitectureRules` | Active |
| Repositories not used by controllers; transactions only in application services; controllers only in `web` | ArchUnit | Active |
| Injected `Clock`; no standard streams, field injection, `java.util.logging`, generic exceptions | ArchUnit + `GeneralCodingRules` | Active |
| Errors: only `@RestControllerAdvice` may declare `@ExceptionHandler`; advice lives in `web` | ArchUnit `ErrorHandlingRules` | Active |
| Errors: controllers never contain `try/catch` | ArchUnit custom condition | Active |
| Errors: every exception under `com.zellastay` extends `ZellaStayException` | ArchUnit | Active |
| Errors: contract, status mapping, localization, no leakage, request id | `ApiExceptionHandlerTest`, `RequestIdLoggingIT` | Active |
| Errors: codes well-formed, unique, namespaced by module, translated fr + en | `ErrorCatalogTest` | Active |
| Cloudinary SDK only inside `media.infrastructure` | ArchUnit `vendorSdkOnlyInMediaInfrastructure` (activates when the SDK is added; proven by fixtures now) | Ready |
| Rules actually detect violations | `ArchitectureRulesDetectViolationsTest` runs every rule against violating fixtures and a clean fixture | Active |
| Files focused and small (≤ 300 lines) | `SourceFileSizeTest` | Active |
| Formatting, `-Xlint:all -Werror` | Spotless, compiler flags | Active |
| Secrets never committed | `.gitignore`, gitleaks run during verification; identity secrets exist only as files or environment variables; CI secret scanning | Planned (CI job with Phase 1) |
| Media: signature construction, size enforcement, private URL authorization, cleanup | Media module tests (see MEDIA_ARCHITECTURE §10) | Planned (Phase 1) |
| Every served route is protected or deliberately public (deny by default): `RouteCatalogTest` enumerates all handler mappings against the audience routes registered by modules and an explicit public allow-list; proven by `RouteCatalogUnitTest`. Allowed/denied/cross-audience behavior per endpoint is covered by the identity integration tests | Active (Slice A); every later slice registers its routes and adds its cross-tenant tests |

## 2. Flutter

| Rule | Mechanism | Status |
|---|---|---|
| Clean Architecture layers (domain pure Dart; data not importing presentation/di; presentation reaching data only through domain and `di`) | Custom import-boundary lint (for example `custom_lint`/`import_lint` rules) | Planned (Flutter scaffold) |
| No feature-to-feature imports; core/shared do not depend on features | Import-boundary lint (same rule set) | Planned (Flutter scaffold) |
| Material/Cupertino imports only inside `core/platform_ui`; `ios/` never imports Material, `android/` never imports Cupertino | Import-boundary lint (same rule set) | Planned (Flutter scaffold) |
| Platform branching (`Platform.isIOS`, `defaultTargetPlatform`) only in `core/platform_ui` | Import-boundary / banned-API lint (same rule set) | Planned (Flutter scaffold) |
| Rendered widget tree never shows Cupertino widgets on Android nor Material widgets on iOS | Shared widget-test helper that pumps every screen with `debugDefaultTargetPlatformOverride` for both platforms and fails on forbidden widget types | Planned (first screens, Phase 1) |
| Network exceptions translated only in `core/network`; HTTP clients restricted to `core/network` and feature data layers | Import-boundary lint (same rule set) | Planned (Flutter scaffold) |
| No `dynamic`; files ≤ 300 lines; no secrets | `analysis_options.yaml` (`avoid_dynamic_calls`, strict modes), a file-length check in CI, secret scanning | Planned (Flutter scaffold) |
| Static analysis strict modes, `dart format`, `flutter analyze` | `analysis_options.yaml`, CI | Planned (Phase 1 scaffold) |
| No duplicated widgets or logic | Review checklist; `packages`-level widget catalog | Review |

## 3. Next.js apps

| Rule | Mechanism | Status |
|---|---|---|
| No `any`, no `@ts-ignore`/`@ts-nocheck` | ESLint `no-explicit-any`, `ban-ts-comment` | Planned (first app) |
| Files ≤ 300 lines, functions ≤ 40 | ESLint `max-lines`, `max-lines-per-function` | Planned (first app) |
| All network calls through `packages/api-client` | ESLint `no-restricted-imports` | Planned (first app) |
| Apps never import each other | dependency-cruiser | Planned (first app) |
| No raw HTML injection | ESLint `react/no-danger` | Planned (first app) |
| No Cloudinary or secret-like variables in frontends; no env files in apps | ESLint restricted names for secret-like variables, secret scanning in CI, a check that apps contain no env files | Planned (first app) |
| Feature structure, import direction `ui → hooks → services → api`, no feature cycles | dependency-cruiser / `eslint-plugin-boundaries` config | Planned (first app) |
| Loading/error/empty/success states for every data view | Component tests per view; review | Planned / Review |
| Error boundaries present in each app | Playwright journeys (404, 500, offline) + scaffold template | Planned |
| No hardcoded user-visible strings | Custom ESLint rule + catalog key parity check | Planned |

## 4. Repository-wide

| Rule | Mechanism | Status |
|---|---|---|
| Docs links valid; `OD-n`/`ADR-n` references defined; no AI attribution in project files | `infra/tools/check-docs.py` (`docs-ci`) | Active |
| Workflows valid and pinned | actionlint (run during verification), actions pinned by SHA, Dependabot | Active |
| Documentation updated with the change | CI heuristics + [REVIEW.md](REVIEW.md) | Review |

## 5. Rules for changing a rule

A rule is relaxed or removed only through an ADR. Every automated rule has a test proving it detects a violation (fixtures for ArchUnit; lint-rule tests for the frontends). When a component is scaffolded, its "Planned" rows become part of that scaffold's definition of done.
