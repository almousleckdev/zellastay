# Roadmap

Phases are ordered by dependency and risk. Dates are intentionally absent; they are set after capacity planning and resolution of blocking open decisions. No phase starts until the documentation foundation is approved. Each phase ends with a production-readiness checklist (security review, load/concurrency tests, observability, runbooks).

## Phase 0 — Foundation (backend foundation implemented; frontends and remaining items pending)
- Repository scaffolding: backend (single Gradle project, package-per-module, ArchUnit/Modulith verification `[ADR-2]`), three Next.js apps, Flutter app, shared packages.
- CI skeleton, Docker Compose for local dependencies (PostgreSQL, Redis), migrations tooling (Flyway), OpenAPI generation pipeline, design tokens.
- Platform skeleton: identity basics, audit, platformconfig (countries, currencies, locales), observability baseline, outbox infrastructure.
- Exit: empty-but-deployable pipeline to a staging environment; architecture tests enforcing module boundaries.
- **Phase 0 extension:** centralized backend error handling (request-id filter, problem factory, global handler, error controller, bilingual bundles, code catalog) and enforcement tooling (architecture rules, frontend standards scanner, `standards-ci`) are implemented; Flutter and Next.js enforcement activates as those apps are scaffolded ([STANDARDS_ENFORCEMENT.md](../engineering/STANDARDS_ENFORCEMENT.md)).
- **Status (2026-11-01):** done — backend project, 22 module packages with enforced dependency contract, Flyway + outbox table, PostgreSQL/Redis dev stack, Dockerfile, CI workflows (backend, docs), architecture and integration tests, typed environment configuration and mounted-secret support. **Not done:** `apps/*` and `packages/*` scaffolding, IaC (`infra/` beyond Docker), OpenAPI generation pipeline, design tokens, audit/identity/platformconfig skeletons beyond empty modules, security starter (deny-by-default) which arrives with the `identity` module, OpenTelemetry tracing, staging deployment. See the Phase 0 report for details.

## Phase 1 — Supply side and verification (**blocked until Phase 0 is approved**)
- Identity (customers, vendor users, admins), organization and staff roles, vendor registration, document upload, verification queue, property drafting, media handling, admin moderation, audit viewer.
- Notification core (templates, outbox, email + in-app first; SMS provider integration validated).
- Media spike (OD-29) before any document or gallery upload ships: verify authenticated delivery for PDFs/scans, malware scanning approach, strict transformation controls, backup/purge behavior, plan limits; L-18 closed before production documents.
- Exit: an approved vendor can create a property that is approved but not yet bookable.

## Phase 2 — Core marketplace transaction (blocking for live payments: OD-18 provider selection, L-1 legal validation; OD-6a timer values)
- Inventory (hotel and private), pricing and quotes, search, favorites, booking (instant + request), cancellation policies, payment module with Airtel Money, Moov Money, cards, refunds, reconciliation, subscriptions and entitlements, guest checkout, push/SMS notifications.
- Public website and customer app booking flows; vendor calendar and reservations; admin finance tools.
- Exit: end-to-end booking in production-like environment, concurrency test suite green, reconciliation proven against provider sandboxes, pilot with a limited vendor cohort.

## Phase 3 — Trust and engagement
- Chat (STOMP/WebSocket), reviews and ratings, support tickets, disputes, vendor financial reports, admin reports.
- Exit: chat authorization verified by tests; reporting reconciles with ledger.

## Phase 4 — Growth
- Loyalty points, promo codes, referrals, seasonal campaigns, vendor campaigns.
- Exit: promotion rules cannot produce negative-revenue bookings (property-based tests).

## Phase 5 — Scale and expansion
- Second country: country config, currency, tax rules, payment providers, locales; legal review per country.
- Kafka introduction if triggers in [SYSTEM_ARCHITECTURE.md](../architecture/SYSTEM_ARCHITECTURE.md) are met; read-model/search engine upgrade; channel manager/iCal; WhatsApp channel; module extraction only if justified by evidence.

## Cross-phase tracks
- Security: threat model per phase, dependency scanning, periodic penetration test before public launch.
- Legal/compliance: validation items in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md) tracked to closure before the dependent phase starts.
- Documentation: updated in the same change as the code (enforced in [REVIEW.md](../engineering/REVIEW.md)).
