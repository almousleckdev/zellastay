# Zella Stay

A global accommodation booking marketplace — hotels, resorts, apartments, villas, guesthouses, hostels, bed and breakfasts and private homes — launching in **Gabon** with an architecture ready for international expansion.

**Status:** documentation foundation approved with decisions recorded; **Phase 0 backend foundation implemented** (module skeleton, architecture tests, PostgreSQL/Redis dev stack, Docker, CI). No business features, payments, or frontends exist yet. See [docs/product/ROADMAP.md](docs/product/ROADMAP.md).

## What it is

| Application | Audience | Technology |
|---|---|---|
| Customer mobile app | Travellers | Flutter + Riverpod |
| Public booking website (incl. vendor self-registration) | Visitors, customers, prospective vendors | Next.js + TypeScript |
| Vendor dashboard | Hotels, hotel groups, property managers, hosts and their staff | Next.js + TypeScript |
| Admin dashboard | Zella Stay operations | Next.js + TypeScript |
| Backend | All of the above | Java 21, Spring Boot, Spring Modulith (modular monolith) |

Supporting infrastructure: PostgreSQL (system of record), Redis (cache, rate limiting, real-time fan-out), Kafka (only when justified), Docker, GitHub Actions. APIs are REST; real-time chat and live updates use WebSocket + STOMP.

## Business model in one paragraph

Customers pay a service fee on successful bookings; hotels and hosts pay monthly or annual subscriptions (Basic, Professional, Enterprise — fully admin-configurable). There is **no per-booking commission** on vendors. Launch payment methods in Gabon: Airtel Money, Moov Money, Visa, Mastercard, behind a country-specific provider abstraction. Details: [BUSINESS_MODEL.md](docs/product/BUSINESS_MODEL.md).

## Key capabilities

Instant Booking and Request to Book · guest checkout · hotel-style and Airbnb-style inventory with database-enforced double-booking prevention · cancellation policies, expiry and refunds · payment idempotency, reconciliation and ledger · mandatory vendor verification · multi-property organizations with role-based staff access · real-time booking-bound chat · centralized multi-channel notifications with transactional outbox · French and English from day one · loyalty, promotions and referrals · reviews, support and disputes · vendor financial reports.

## Architecture at a glance

- Modular monolith; 21 explicit modules plus a shared kernel with a documented, acyclic dependency graph enforced in CI.
- Clean Architecture + DDD inside each module (`api` / `domain` / `application` / `infrastructure` / `web`).
- Multi-tenant vendor isolation by organization, with defense in depth.
- Multi-country by configuration (country, currency, locale, tax, payment providers are data).
- Reliable side effects through the transactional outbox; notification or provider failures never roll back bookings or payments.

## Documentation

Start at [docs/README.md](docs/README.md). Highlights:

| Area | Documents |
|---|---|
| Product | [PRD](docs/product/PRD.md) · [Business model](docs/product/BUSINESS_MODEL.md) · [Features](docs/product/FEATURES.md) · [User flows](docs/product/USER_FLOWS.md) · [Booking lifecycle](docs/product/BOOKING_LIFECYCLE.md) · [Roadmap](docs/product/ROADMAP.md) |
| Architecture | [System](docs/architecture/SYSTEM_ARCHITECTURE.md) · [Modules](docs/architecture/MODULAR_ARCHITECTURE.md) · [Database](docs/architecture/DATABASE_DESIGN.md) · [API](docs/architecture/API_SPECIFICATION.md) · [Security](docs/architecture/SECURITY_ARCHITECTURE.md) · [Payments](docs/architecture/PAYMENT_ARCHITECTURE.md) · [Notifications](docs/architecture/NOTIFICATION_ARCHITECTURE.md) · [Chat](docs/architecture/CHAT_ARCHITECTURE.md) · [Inventory](docs/architecture/INVENTORY_ARCHITECTURE.md) · [Media](docs/architecture/MEDIA_ARCHITECTURE.md) · [i18n](docs/architecture/INTERNATIONALIZATION.md) |
| Frontend | [Customer mobile](docs/frontend/CUSTOMER_MOBILE.md) · [Next.js architecture](docs/frontend/WEB_ARCHITECTURE.md) · [Public website](docs/frontend/PUBLIC_WEBSITE.md) · [Vendor dashboard](docs/frontend/VENDOR_DASHBOARD.md) · [Admin dashboard](docs/frontend/ADMIN_DASHBOARD.md) · [Design system](docs/frontend/DESIGN_SYSTEM.md) |
| Engineering | [Coding standards](docs/engineering/CODING_STANDARDS.md) · [Testing](docs/engineering/TESTING_STRATEGY.md) · [DevOps](docs/engineering/DEVOPS.md) · [CI/CD](docs/engineering/CI_CD.md) · [Observability](docs/engineering/OBSERVABILITY.md) · [Review](docs/engineering/REVIEW.md) · [Error handling](docs/engineering/ERROR_HANDLING.md) · [Standards enforcement](docs/engineering/STANDARDS_ENFORCEMENT.md) |
| Decisions | [ADRs](docs/DECISIONS.md) · [Open decisions, legal register, risks](docs/OPEN_DECISIONS.md) |

## Repository layout

Present today:

```
CLAUDE.md          Permanent instructions for AI coding agents
README.md
docs/              Product, architecture, frontend and engineering documentation
backend/           Spring Boot modular monolith (Java 21, Gradle, Spring Modulith)
infra/docker/      Local PostgreSQL + Redis (+ optional backend container) via Docker Compose
infra/scripts/     Local run script and documentation checks
.github/           CI workflows (backend-ci, docs-ci) and Dependabot
```

Planned (created when each component is scaffolded): `apps/customer-mobile` (Flutter), `apps/public-web`, `apps/vendor-dashboard`, `apps/admin-dashboard` (Next.js), `packages/` (shared web packages).

## Engineering principles

Clean Architecture · DDD · SOLID · explicit module ownership · single responsibility per file · no duplicated business logic · no unnecessary abstractions · no premature microservices · strong typing · secure defaults · test-first development · no static or randomly generated production data · no hardcoded secrets or environment configuration. Full rules: [CODING_STANDARDS.md](docs/engineering/CODING_STANDARDS.md).

## Contributing

1. Read the relevant documents before changing anything; `CLAUDE.md` applies to AI agents, and the same rules apply to humans.
2. Write tests first; run all relevant checks; list any you could not run.
3. Update documentation in the same change as the code.
4. Follow the [review checklist](docs/engineering/REVIEW.md). Small, focused pull requests; Conventional Commits.
5. Unresolved business or legal questions are tracked in [OPEN_DECISIONS.md](docs/OPEN_DECISIONS.md) — raise them, don't guess.

## Local development

Requirements: JDK 21, Docker (running).

```bash
cp infra/docker/.env.example infra/docker/.env      # then set ZS_DB_PASSWORD and ZS_REDIS_PASSWORD
docker compose -f infra/docker/compose.yaml --env-file infra/docker/.env up -d
infra/scripts/run-backend-local.sh                   # backend on http://localhost:8080, health on :8081
cd backend && ./gradlew build                        # format check, tests (Testcontainers), coverage
```

Details, environment variables and secret handling: [docs/engineering/DEVOPS.md](docs/engineering/DEVOPS.md) §12.

## License

To be decided by the owners.
