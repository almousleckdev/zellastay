# Coding Standards

Applies to all code. Language-specific sections follow universal rules. Where a rule conflicts with a framework convention, the rule here wins unless an ADR says otherwise.

## 1. Universal principles

1. Clean Architecture and DDD: dependencies point inward; the domain knows nothing about HTTP, SQL, JSON or frameworks beyond what [ADR-4](../DECISIONS.md) permits.
2. SOLID, applied pragmatically. **No unnecessary abstractions:** an interface needs a second implementation, a module boundary, or a test seam that matters. Do not create "manager", "helper", "util" grab-bags or single-implementation factories without need.
3. **Single responsibility per file** and per class. Files stay focused and reasonably sized: target ≤ 300 lines per file, ≤ 40 lines per method/function; exceeding requires a justified split or exception noted in review.
4. **No duplicated business logic.** A business rule has exactly one home (domain object or domain service in the owning module). Clients display; they do not re-implement rules (price, fee, refund, availability, permissions).
5. Strong typing everywhere: typed IDs, value objects for money/dates/ranges, enums/sealed types for states, no stringly-typed states, no `Object`/`any`/`dynamic` escape hatches without justification.
6. Secure defaults: deny by default, least privilege, validated input, safe error messages.
7. **Test-first development:** write the failing test, then the code ([TESTING_STRATEGY.md](TESTING_STRATEGY.md)).
8. **No static or randomly generated production data:** no hardcoded business data (countries, fees, plans, tax rates, URLs, templates) in code — it lives in configuration/database. No dummy/seed "fake" data in production paths; test data lives under test sources/fixtures and factories only.
9. **No hardcoded secrets or environment-specific configuration** (hosts, URLs, keys, bucket names). Config through typed configuration properties from environment/secret manager; validated at startup (fail fast).
10. **Comments:** avoid unnecessary comments; code, names and types explain *what*. Comment only *why* when non-obvious (constraint, workaround with link to issue, legal reason). No commented-out code, no TODOs without an issue reference, no change-log comments.
11. **No AI attribution** anywhere in project files (code, docs, commit trailers in project-generated artifacts, PR text), except where tooling requirements for commit trailers are explicitly configured by the maintainers.
12. Errors are explicit: domain errors as typed exceptions/results with stable codes; never swallow exceptions; never use exceptions for normal control flow in hot paths.
13. Logging: structured, no PII/secrets, correlation ids, levels used consistently.
14. Money is never a floating-point type. Time comes from an injected `Clock` (backend) or injectable time source, never `now()` directly in domain logic.

## 2. Backend (Java 21, Spring Boot, Spring Modulith)

### 2.1 Structure
- Package layout per module: `api`, `domain`, `application`, `infrastructure`, `web` ([MODULAR_ARCHITECTURE.md](../architecture/MODULAR_ARCHITECTURE.md) §3).
- Module dependency rules are enforced by tests; **no circular dependencies**.
- Controllers contain no business logic: parse → call one application service method → map. No repositories or entities in controllers. Request/response models are separate from domain and persistence models.
- Repositories: interfaces in `domain`; implementations in `infrastructure.persistence`. Application services never use `EntityManager`/SQL directly.
- Transactions only in `application` services (`@Transactional` on the use-case method). Critical flows (booking creation, payment confirmation, refunds, cancellations, subscription state changes) are single-transaction units whose boundaries are documented in their module docs and tested.
- No field injection; constructor injection; no static mutable state; no service locators.
- Application events: publish through the domain/application layer using the outbox mechanism; listeners idempotent.

### 2.2 Java language
- Java 21 features encouraged where they clarify: records for DTOs/value objects, sealed interfaces for closed hierarchies (states, results), pattern matching, `switch` expressions.
- Immutability by default; `final` fields; defensive copies at boundaries; collections returned unmodifiable.
- Nullability: `Optional` only as return type; parameters/fields non-null by contract (JSpecify/`@NonNull` package-level defaults); validation at boundaries.
- Naming: packages lowercase; types `PascalCase`; no `Impl` suffix unless unavoidable (prefer intention-revealing: `JpaBookingRepository`); methods verbs; booleans `is/has/can`.
- Formatting and static analysis: Spotless (google-java-format variant), Checkstyle/PMD or Error Prone, SpotBugs; build fails on violations.
- Virtual threads allowed for blocking I/O; no synchronized blocks around I/O (pinning); no unbounded thread creation.
- JPA: explicit fetch strategies, no `EAGER`, no open-session-in-view, no entities across module APIs, no lazy loading outside the transaction, `version` for optimistic locking, explicit `@Table` names with module prefix; native SQL/jOOQ for locking and complex queries, with SQL in dedicated query classes.
- Validation: Bean Validation on request models; invariants enforced in domain constructors/methods.
- Money: `Money` value object; arithmetic through it only.
- Exceptions: domain exception hierarchy mapped to Problem Details in one `@ControllerAdvice` per audience; no exception messages exposed with internals.
- Configuration: `@ConfigurationProperties` records, validated, one per concern; profiles only for environment-agnostic behavior toggles, never for secrets.

### 2.3 Database and migrations
- Flyway migrations are append-only; never edit applied migrations; one concern per migration; expand/contract for destructive changes; every migration reviewed with [DATABASE_DESIGN.md](../architecture/DATABASE_DESIGN.md) updates.
- Constraints (NOT NULL, CHECK, UNIQUE, exclusion) are part of the design, not optional.

### 2.4 API
- Follow [API_SPECIFICATION.md](../architecture/API_SPECIFICATION.md). Public DTOs are versioned and never reuse persistence models.

## 3. TypeScript / Next.js

- `strict: true`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`; no `any` (lint error), no non-null assertions without justification; `unknown` + narrowing at boundaries.
- API types come from the generated client; no hand-written duplicates; runtime validation (Zod) for external input and form data.
- Server Components by default; `"use client"` only where needed; data fetching in server components or TanStack Query hooks; no business rules in components.
- One component per file, named exports, colocated tests; hooks start with `use`; file names `kebab-case`, components `PascalCase`.
- Styling through design tokens/Tailwind theme only; no raw colors/spacing values.
- All user-visible text through i18n; ESLint rule enforces.
- Accessibility lint (`jsx-a11y`) enforced; no `dangerouslySetInnerHTML` without sanitization and review.
- ESLint + Prettier + `tsc --noEmit` clean; import boundaries enforced (apps may import `packages/*`, never other apps).
- Environment: only `NEXT_PUBLIC_*` for public values; secrets server-side only.

## 4. Dart / Flutter

- `analysis_options.yaml` with strict modes; `flutter analyze` clean; `dart format` enforced.
- Riverpod for state and DI; providers are small and single-purpose; no global singletons; no business logic in widgets (widgets render state, call notifiers).
- Immutable models (sealed classes/records/freezed); no `dynamic`; no `!` without justification.
- Feature-first folders per [CUSTOMER_MOBILE.md](../frontend/CUSTOMER_MOBILE.md); no cross-feature imports except through `shared`/`core`.
- All strings in ARB; all dimensions/colors from theme tokens.

## 5. SQL and scripts

- SQL keywords uppercase, identifiers snake_case; no `SELECT *` in application queries; parameterized always.
- Shell scripts: `set -euo pipefail`, shellcheck-clean; no secrets in scripts.

## 6. Git, commits, branches

- Trunk-based with short-lived branches `feat/…`, `fix/…`, `docs/…`; PRs small and focused (one concern, ideally < 400 changed lines excluding generated).
- Conventional Commits (`feat(booking): …`); subject ≤ 72 chars, imperative; body explains why. Commit trailers follow maintainers' configuration.
- Main is always releasable; direct pushes to main prohibited; required checks and review ([REVIEW.md](REVIEW.md)).
- Generated code is not hand-edited and is excluded from size rules.

## 7. Dependencies

Adding a dependency requires: necessity, license compatibility, maintenance health, security review, size impact; pinned versions with lockfiles; Renovate/Dependabot updates; no duplicate libraries for the same job.

## 8. Documentation in code

Public APIs of modules (`api` packages) have Javadoc describing contract and error cases (this is *contract documentation*, not narration). Architecture-affecting changes update `docs/` in the same PR.
