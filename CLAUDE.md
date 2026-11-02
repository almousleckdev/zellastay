# Zella Stay — Permanent Project Instructions

Zella Stay is a production-grade, international accommodation marketplace (Booking.com / Airbnb / Trip.com / Expedia class), launching in **Gabon** and built to expand to other countries. It is **not** a prototype or demo.

Stack: Java 21 + Spring Boot + Spring Modulith (modular monolith), PostgreSQL, Redis, Kafka only when justified, Flutter + Riverpod (customer mobile), Next.js + TypeScript (public website, vendor dashboard, admin dashboard), REST + WebSocket/STOMP, Docker, GitHub Actions.

## Current phase

Documentation is approved with decisions ADR-13 (approve-then-pay) and ADR-20 (non-custodial funds flow) recorded. **Phase 0 (backend foundation) is approved. Phase 1 is in progress, slice by slice (A identity and audit, B organizations and staff, C verification, D catalog, E room types and units, F media); Slice A (identity and audit) is implemented and awaiting review; each slice is reported before the next starts (see `docs/superpowers/plans/2026-11-02-phase1-slice-a-identity-audit-plan.md`).** Booking, payments, subscriptions and all frontends are NOT started** and must not be started until the user approves a roadmap phase (`docs/product/ROADMAP.md`). No live payment transaction may ever run before legal item L-1 is closed, and no payment provider is selected (OD-18).

## Documentation map (read before changing anything)

| Topic | Read |
|---|---|
| Requirements, scope, requirement IDs | `docs/product/PRD.md`, `FEATURES.md` |
| Revenue, subscriptions, funds flow | `docs/product/BUSINESS_MODEL.md` |
| Booking states, timers, cancellation, refunds | `docs/product/BOOKING_LIFECYCLE.md` |
| User journeys | `docs/product/USER_FLOWS.md` |
| System and module structure, **allowed module dependencies** | `docs/architecture/SYSTEM_ARCHITECTURE.md`, `MODULAR_ARCHITECTURE.md` |
| Tables, constraints, ownership | `docs/architecture/DATABASE_DESIGN.md` |
| API conventions and endpoints | `docs/architecture/API_SPECIFICATION.md` |
| AuthN/AuthZ, tenancy, audit, privacy | `docs/architecture/SECURITY_ARCHITECTURE.md` |
| Payments, ledger, reconciliation | `docs/architecture/PAYMENT_ARCHITECTURE.md` |
| Inventory and concurrency | `docs/architecture/INVENTORY_ARCHITECTURE.md` |
| Notifications and outbox | `docs/architecture/NOTIFICATION_ARCHITECTURE.md` |
| Chat | `docs/architecture/CHAT_ARCHITECTURE.md` |
| i18n / multi-country | `docs/architecture/INTERNATIONALIZATION.md` |
| Frontends and design system | `docs/frontend/*` |
| Engineering rules | `docs/engineering/*` (`CODING_STANDARDS.md`, `TESTING_STRATEGY.md`, `REVIEW.md`, …) |
| Error handling (all tiers) | `docs/engineering/ERROR_HANDLING.md` |
| Enforcement of every mandatory rule | `docs/engineering/STANDARDS_ENFORCEMENT.md` |
| Media / Cloudinary | `docs/architecture/MEDIA_ARCHITECTURE.md` |
| Flutter architecture and native UI | `docs/frontend/CUSTOMER_MOBILE.md` §2–§3 |
| Next.js architecture (3 web apps) | `docs/frontend/WEB_ARCHITECTURE.md` |
| Decisions and open questions | `docs/DECISIONS.md`, `docs/OPEN_DECISIONS.md` |

## How Claude must work

1. **Read first.** Before any change, read the documentation relevant to the area. Reference requirement IDs (e.g., FR-BK-1) in work and tests.
2. **Never invent requirements.** If the docs don't say it, it isn't approved. Do not add features, fields, endpoints, states or rules that aren't documented.
3. **Ask when a critical decision is unclear.** Anything in `docs/OPEN_DECISIONS.md` (especially OD-18, OD-6a and every `[LEGAL]` item; L-1 is mandatory before any live payment) must be asked, not assumed. State the options and a recommendation.
4. **Keep docs and code in sync.** When business rules, architecture, module dependencies, schema, API contracts, security rules or configuration change, update the corresponding `docs/` files in the same change. If code and docs disagree, stop and surface the conflict.
5. **Stay in approved scope.** Implement only the requested, documented task of the current roadmap phase. No drive-by refactors, no speculative abstractions.
6. **Test first.** Write the failing test, then the implementation. Run the relevant tests and checks (format, lint, type-check, unit, integration, architecture tests) and report results truthfully.
7. **Report unexecuted checks.** At the end of every task, list the checks you did not run (or could not run) and why. Never claim a check passed that you didn't run.
8. **Review before done.** Perform a code review against `docs/engineering/REVIEW.md` before declaring any task complete; list findings and what was fixed.
9. **No unverified legal/regulatory assumptions.** Mark such items `[LEGAL]` and add them to the register in `docs/OPEN_DECISIONS.md`.

## Non-negotiable engineering rules

- Clean Architecture, DDD, SOLID; explicit module ownership; single responsibility per file; keep files focused (≤ ~300 lines) and methods short.
- **Module boundaries:** use other modules only through their `api` package; no cross-module table access; **no circular dependencies**; any new dependency must be added to the graph in `MODULAR_ARCHITECTURE.md` §4 in the same change.
- **Layering:** `web` → `application` → `domain` ← `infrastructure`. No business logic in controllers. Repository interfaces in `domain`, implementations in `infrastructure`. Transactions in `application` only.
- Critical workflows (booking creation, payment confirmation, refunds, cancellation, subscription state) are transactional; side effects (notifications, provider calls) go through the transactional outbox/after-commit and must never roll back bookings or payments.
- Inventory correctness is enforced by database constraints and atomic conditional updates; Redis never guarantees exclusivity.
- Payments are non-custodial (ADR-20): never design Zella Stay to hold customer funds. Idempotency at all three layers, webhook verification and dedupe, re-validate amounts server-side, double-entry ledger, reconciliation.
- Authorization is enforced in the application layer with deny-by-default, object-level (tenant/ownership) checks, and tests for allowed/denied/cross-tenant.
- Prices, fees, availability and permissions are computed server-side only.
- Money = integer minor units + currency; never floats. Time via injected clock; stays in property-local dates.
- Strong typing; no `any`/`dynamic`/untyped escape hatches without justification.
- **No static or randomly generated production data.** No dummy data in production code paths. Reference/config data lives in migrations/config tables.
- **No hardcoded secrets or environment-specific configuration.** Typed config from environment/secret manager; `.env.example` has names only.
- Avoid unnecessary comments (explain *why* only when non-obvious). No commented-out code.
- No unnecessary abstractions; no duplicated business logic; no premature microservices; Kafka only when a documented trigger occurs.
- i18n: no user-visible hardcoded strings; French is default, English supported; every template/message exists in both.
- **No AI attribution in project files** (code, docs, README, config). Commit message trailers are governed by the user's/tooling's configured attribution instructions only.

## Mandatory engineering standards (permanent; apply to all phases)

**Centralized error handling** (`docs/engineering/ERROR_HANDLING.md`)
- Backend: all API errors are RFC 9457 problem+json built only by `ApiExceptionHandler`/`ProblemFactory`. Throw specific `ZellaStayException` subclasses carrying a module-namespaced `ErrorCode` enum constant; add its French and English messages. Controllers never use `try/catch` or `@ExceptionHandler`; adapters translate third-party failures at the boundary. Never expose stack traces, class names, SQL, constraint names, secrets or rejected values. Every response and log line carries the `requestId`.
- Flutter: failures are translated once in `core/network` into the shared `Failure` model; features never touch Dio/HTTP exceptions; errors render through the shared widgets.
- Next.js: all network calls go through `packages/api-client` with typed errors; use shared `ErrorState`/`EmptyState`/loading components and error boundaries; never show raw error messages.

**Flutter** (`docs/frontend/CUSTOMER_MOBILE.md`)
- Clean Architecture per feature (`domain` pure Dart, `data`, `di`, `presentation`); Riverpod for state and DI; no feature-to-feature imports; presentation never imports data.
- One responsibility per class and file; files ≤ 300 lines; strict DRY — search `shared/` and `core/platform_ui` before creating a widget; extract reusable widgets/providers/models/repositories/services into their folders.
- **Native UI is non-negotiable:** iOS = Cupertino only, Android = Material only, never mixed. All platform-specific UI and any `Platform`/`defaultTargetPlatform` branching live in `core/platform_ui` (`ios/` imports Cupertino only, `android/` Material only). Features import neither `material.dart` nor `cupertino.dart`.

**Next.js** (`docs/frontend/WEB_ARCHITECTURE.md`)
- Feature-oriented (`features/<f>/{ui,hooks,services,api,schemas,types}`); reuse `packages/ui`, `packages/api-client`, `packages/schemas`; no duplicated components, API logic, validation or business logic; strict TypeScript, no `any`/`@ts-ignore`; files ≤ 300 lines; apps never import each other; every data view has loading, error, empty and success states.

**Media / Cloudinary** (`docs/architecture/MEDIA_ARCHITECTURE.md`)
- Only the `media` module (`media.infrastructure`) may use the Cloudinary SDK; other modules use `MediaApi` and `MediaAssetId`. Store public IDs and metadata in PostgreSQL, never binaries. Uploads are server-authorized and server-validated (signed direct upload for galleries/videos, server-proxied for documents/avatars/chat images); sizes are verified server-side. Private files use authenticated delivery with short-lived signed URLs and audit. Never put the API secret in any frontend. Replace by new asset then delete; clean up orphans. Do not build document upload before the OD-29 spike and L-18.

**Enforcement** (`docs/engineering/STANDARDS_ENFORCEMENT.md`)
- Every rule above has an automated check or a stated review item. Run `./gradlew build` (backend, includes architecture and error tests) and `python3 -I infra/scripts/check_standards.py` plus its tests; when scaffolding an app, activate its "Planned" rows. Never weaken or skip a check; relaxing a rule requires an ADR.

## Scope boundaries

Four independent frontends (`apps/customer-mobile`, `apps/public-web`, `apps/vendor-dashboard`, `apps/admin-dashboard`) and one backend (`backend/`). Shared web code lives in `packages/`. These directories are created in roadmap Phase 0, not before.

## Commands

Backend (from `backend/`; Docker must be running for Testcontainers): `./gradlew build` (format check + compile + all tests + JaCoCo), `./gradlew spotlessApply` (format), `./gradlew test --tests '<pattern>'`. Docs check from the repo root: `python3 -I infra/scripts/check-docs.py`; frontend standards scan and its tests: `python3 -I infra/scripts/check_standards.py` and `python3 -I -m unittest discover -s infra/scripts -t infra/scripts`. Local stack and run script: `docs/engineering/DEVOPS.md` §12. Add commands for each frontend here when it is scaffolded.

When adding a module dependency: edit `docs/architecture/MODULAR_ARCHITECTURE.md` §4 **and** the module's `package-info.java` `allowedDependencies`; `ModuleStructureTest` fails if they differ. When adding or changing a domain event, update `docs/architecture/EVENT_CATALOG.md`.

## Git

Trunk-based, short-lived branches, Conventional Commits, small PRs, no force-push to `main`, required reviews and checks (`docs/engineering/CI_CD.md`). Do not commit or push unless the user asks.
