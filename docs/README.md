# Documentation Index

Reading order for newcomers: PRD → Business model → Booking lifecycle → System architecture → Modular architecture → Security → the specific area you work on → Coding standards → Review.

## Product (`product/`)
- [PRD.md](product/PRD.md) — vision, requirements with IDs, NFRs
- [BUSINESS_MODEL.md](product/BUSINESS_MODEL.md) — revenue, subscriptions, funds flow
- [FEATURES.md](product/FEATURES.md) — feature → module → surface → phase
- [USER_FLOWS.md](product/USER_FLOWS.md)
- [BOOKING_LIFECYCLE.md](product/BOOKING_LIFECYCLE.md) — authoritative state machine
- [ROADMAP.md](product/ROADMAP.md)

## Architecture (`architecture/`)
- [SYSTEM_ARCHITECTURE.md](architecture/SYSTEM_ARCHITECTURE.md) — includes the index of the 14 mandated concerns
- [MODULAR_ARCHITECTURE.md](architecture/MODULAR_ARCHITECTURE.md) — module catalog and the dependency contract
- [DATABASE_DESIGN.md](architecture/DATABASE_DESIGN.md)
- [API_SPECIFICATION.md](architecture/API_SPECIFICATION.md)
- [SECURITY_ARCHITECTURE.md](architecture/SECURITY_ARCHITECTURE.md)
- [PAYMENT_ARCHITECTURE.md](architecture/PAYMENT_ARCHITECTURE.md)
- [NOTIFICATION_ARCHITECTURE.md](architecture/NOTIFICATION_ARCHITECTURE.md)
- [CHAT_ARCHITECTURE.md](architecture/CHAT_ARCHITECTURE.md)
- [INVENTORY_ARCHITECTURE.md](architecture/INVENTORY_ARCHITECTURE.md)
- [INTERNATIONALIZATION.md](architecture/INTERNATIONALIZATION.md)
- [MEDIA_ARCHITECTURE.md](architecture/MEDIA_ARCHITECTURE.md) — Cloudinary media and documents
- [PAYMENT_PROVIDER_RESEARCH.md](architecture/PAYMENT_PROVIDER_RESEARCH.md) — OD-18 research, no selection
- [EVENT_CATALOG.md](architecture/EVENT_CATALOG.md) — domain events
- [MULTI_COUNTRY.md](architecture/MULTI_COUNTRY.md) — countries, markets, jurisdictions, governing-country matrix, commission, cross-border
- [TENANCY.md](architecture/TENANCY.md) — organization tenancy and the RLS gate
- [API_CONTRACTS.md](architecture/API_CONTRACTS.md) — OpenAPI as the single contract source

## Frontend (`frontend/`)
[CUSTOMER_MOBILE.md](frontend/CUSTOMER_MOBILE.md) · [WEB_ARCHITECTURE.md](frontend/WEB_ARCHITECTURE.md) · [PUBLIC_WEBSITE.md](frontend/PUBLIC_WEBSITE.md) · [VENDOR_DASHBOARD.md](frontend/VENDOR_DASHBOARD.md) · [ADMIN_DASHBOARD.md](frontend/ADMIN_DASHBOARD.md) · [DESIGN_SYSTEM.md](frontend/DESIGN_SYSTEM.md)

## Engineering (`engineering/`)
[CODING_STANDARDS.md](engineering/CODING_STANDARDS.md) · [TESTING_STRATEGY.md](engineering/TESTING_STRATEGY.md) · [DEVOPS.md](engineering/DEVOPS.md) · [CI_CD.md](engineering/CI_CD.md) · [OBSERVABILITY.md](engineering/OBSERVABILITY.md) · [REVIEW.md](engineering/REVIEW.md) · [ERROR_HANDLING.md](engineering/ERROR_HANDLING.md) · [STANDARDS_ENFORCEMENT.md](engineering/STANDARDS_ENFORCEMENT.md) · [DEVELOPMENT_PROCESS.md](engineering/DEVELOPMENT_PROCESS.md) · [RAILWAY_DEPLOYMENT.md](engineering/RAILWAY_DEPLOYMENT.md) · [RUNBOOKS.md](engineering/RUNBOOKS.md)

## Phase 1 working documents (`superpowers/`)
- [Slice A design: Identity and Audit](superpowers/specs/2026-11-02-phase1-slice-a-identity-audit-design.md)
- [Slice A implementation plan](superpowers/plans/2026-11-02-phase1-slice-a-identity-audit-plan.md)

## Decisions
- [DECISIONS.md](DECISIONS.md) — accepted architecture decisions
- [OPEN_DECISIONS.md](OPEN_DECISIONS.md) — open business/technical decisions, `[LEGAL]` register, risks

## Conventions used in the documents
- `[OD-n]` → open decision in OPEN_DECISIONS.md. `[LEGAL]` → needs local legal or provider validation. `[ADR-n]` → decision in DECISIONS.md.
- FR-/NFR- identifiers refer to PRD requirements.
- Status values (booking, payment, subscription, verification) have exactly one definition each; other documents link to it rather than redefine it.
