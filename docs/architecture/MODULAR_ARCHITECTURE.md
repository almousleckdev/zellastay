# Modular Architecture

## 1. Rules

1. The backend is one deployable organized as Spring Modulith **application modules**: one top-level package per module under `com.zellastay`.
2. A module may use another module only through that module's **published API** (`api` package: facade interfaces, DTOs, events). Everything else is internal.
3. A module owns its tables. Table names are prefixed with the module (`booking_`, `inv_`, …). No cross-module joins, foreign keys across module boundaries are limited to **opaque identifier columns without DB foreign keys** unless both tables are in the same module (see [DATABASE_DESIGN.md](DATABASE_DESIGN.md) §2).
4. **Circular dependencies between modules are prohibited.** The dependency graph in §4 is the contract. CI runs `ApplicationModules.verify()` and ArchUnit rules; a cycle or an undeclared dependency fails the build.
5. A new inter-module dependency requires editing §4 in the same change, with a justification, and review.
6. Cross-module state propagation uses domain events; cross-module reads/commands use API facades. Prefer events when the caller does not need an immediate answer.
7. `shared` (shared kernel) holds only: value objects (`Money`, `CurrencyCode`, `CountryCode`, `LocaleCode`, typed IDs, `DateRange`), the `Clock` port, base exception types, and the event base type. It contains **no business logic**.

## 2. Module catalog

| Module | Owns (responsibility) |
|---|---|
| `shared` | Shared kernel (see rule 7). |
| `platformconfig` | Countries, currencies, locales, time zones, tax rules, required-document sets, enabled payment methods per country, service-fee rules, platform parameters (timers, thresholds), feature flags. |
| `audit` | Append-only audit log: recording API and admin query. |
| `identity` | Accounts (customer, vendor user, admin), credentials, sessions/refresh tokens, verification (email/phone), device registrations, guest booking-access tokens, platform roles, user profile and preferences root. |
| `media` | Upload pipeline, object storage, image variants, galleries' file records, private documents, signed URLs, antivirus/validation hooks. |
| `onboarding` | Verification cases (subject = organization or property, by opaque reference), submissions with versions, document requirements checklist, reviewer decisions, rejection reasons, resubmission history. |
| `payment` | Provider abstraction, payment attempts, payments, refunds, ledger (accounting mirror), webhooks, reconciliation, settlement beneficiaries and settlement records, idempotency for payment operations. Admin-facing endpoints only; vendor-facing views live in `organization` (beneficiary setup) and `reporting` (settlement statements) to keep the graph acyclic. |
| `subscription` | Plans, prices, entitlements, vendor subscriptions, subscription invoices, lifecycle, `EntitlementApi`. |
| `organization` | Vendor organizations (tenants), members, roles, property scopes, invitations, permission evaluation (`VendorAccessApi`). |
| `catalog` | Properties (type, address, geolocation, content, amenities, rules, check-in instructions, cancellation policy selection, booking mode), property approval state, nearby attractions. |
| `inventory` | Sellable units (room types, rooms, private units), capacity, availability calendar, holds and allocations (double-booking prevention). |
| `pricing` | Rate plans, seasonal rules, fees (cleaning, deposit), taxes application, **quote** computation, service fee application. |
| `promotion` | Promo codes, vouchers, campaigns (platform and vendor), discount evaluation and redemption ledger. |
| `booking` | Booking aggregate, state machine, cancellation policy evaluation, refund computation, booking history, orchestration of quote → hold → payment. |
| `search` | Search read model (denormalized, fed by events), filters, geo search, ranking. |
| `engagement` | Favorites and wishlists. |
| `review` | Reviews, ratings aggregates, vendor responses, moderation state. |
| `chat` | Conversations, participants, messages, attachments refs, delivery/read state, unread counters, WebSocket/STOMP endpoints, retention. |
| `notification` | Templates, localization, preferences, channel routing, delivery tracking, retries, provider adapters, in-app inbox. |
| `loyalty` | Points ledger, tiers/rewards, referrals, reward history. Converts rewards into vouchers via `promotion`. |
| `support` | Support tickets, disputes, complaints, resolutions. |
| `reporting` | Read models for vendor financial reports and admin dashboards. |

## 3. Internal layout of a module

```
com.zellastay.<module>/
  api/             Published: facade interfaces, command/result DTOs, domain events  (Modulith named interface)
  domain/          Aggregates, entities, value objects, domain services, repository interfaces (ports), domain events impls
  application/     Use-case services (transaction boundary), command/query handlers, event listeners
  infrastructure/  Adapters: JPA/jOOQ repositories, provider clients, scheduled jobs, config
  web/             Controllers + request/response models, grouped by audience: customer/, vendor/, admin/, public/
```

Rules inside a module:
- `web` depends on `application`; `application` depends on `domain`; `infrastructure` implements `domain` ports. `domain` depends on nothing outside `shared`.
- Controllers: parse/validate input, call exactly one application service method, map the result. No business rules, no repositories, no transactions.
- Repository interfaces are declared in `domain`; implementations (JPA entities, SQL) are in `infrastructure/persistence`. JPA entities are persistence models; aggregates remain domain objects where invariants warrant it, otherwise a documented pragmatic mapping is allowed `[ADR-4]`.
- Transactions are opened in `application` services only.

## 4. Allowed dependency graph (the contract)

Arrow `A → B` = A may call B's `api` or listen to B's events. Anything not listed is forbidden.

```
shared                : (none)
platformconfig        → shared
audit                 → shared
identity              → shared, platformconfig, audit
media                 → shared, identity, audit
payment               → shared, platformconfig, identity, audit
onboarding            → shared, platformconfig, identity, media, audit
subscription          → shared, platformconfig, payment, onboarding, audit
organization          → shared, identity, onboarding, subscription, payment, audit
catalog               → shared, platformconfig, organization, onboarding, subscription, media, audit
inventory             → shared, catalog, organization, subscription, audit
pricing               → shared, platformconfig, inventory, audit
promotion             → shared, platformconfig, organization, catalog, audit
booking               → shared, platformconfig, identity, organization, catalog, inventory, pricing, promotion, payment, audit
engagement            → shared, identity, catalog
review                → shared, identity, booking, catalog, media, audit
chat                  → shared, identity, booking, organization, media, audit
support               → shared, identity, booking, payment, organization, media, audit
loyalty               → shared, identity, booking, promotion, platformconfig, audit
search                → shared, platformconfig, catalog, inventory, pricing, review, subscription
notification          → shared, platformconfig, identity, onboarding, subscription, organization, catalog, promotion, booking, payment, chat, review, support, loyalty
reporting             → shared, booking, payment, subscription, organization, catalog, review (read-only)
```

Layering (a module may only depend on modules above it in this list; same-level dependencies must be listed explicitly above):

```
L0 shared
L1 platformconfig, audit
L2 identity, media, payment
L3 onboarding, subscription
L4 organization
L5 catalog
L6 inventory, promotion, engagement
L7 pricing
L8 booking
L9 review, chat, support, loyalty, search
L10 notification, reporting
```

Notes that resolve potential cycles:
- **Payment never depends on booking or subscription.** Callers pass an opaque `PaymentReference(type, id)`; payment emits `PaymentSucceeded/Failed`, `RefundSucceeded/Failed` carrying that reference; booking and subscription listen. Booking requests refunds by calling `PaymentApi.requestRefund` inside its cancellation transaction.
- **Onboarding is subject-agnostic.** It stores verification cases with opaque subject refs and a display snapshot; organization and catalog submit cases and listen to `VerificationDecided`. Subscription listens to `VerificationDecided` (subject = organization) to start a trial.
- **Entitlement checks** flow downward only: organization, catalog, inventory call `EntitlementApi` (subscription); subscription never imports them.
- **Loyalty vs booking:** booking applies discounts through `promotion`; loyalty listens to booking events and mints vouchers in `promotion`. Booking does not depend on loyalty.
- **Notification** depends on no business module's internals; it only consumes the published events of the modules on its line above (the list is exhaustive and tested) and reads recipient contact data via `identity` API. No module depends on notification, so adding a notifiable event never creates a cycle.
- **Search** is a read model: it consumes events and never calls back.

The enforced version of this graph lives in a test class (`ModuleDependencyRulesTest`) generated from this table by hand; changing one without the other fails review.

## 5. Module interaction patterns

| Need | Pattern |
|---|---|
| Ask another module for data now | Call its `api` facade (read-only query methods return DTOs, not entities). |
| Command another module in same transaction | Call its `api` command facade; allowed only when atomicity is required (e.g., booking → inventory hold, booking → payment initiation record). |
| Tell the world something happened | Publish a domain event (outbox). |
| Reference another module's entity | Store its typed ID only. |
| Need a lot of cross-module data for a screen | Build a read model in the consuming module (search, reporting) from events. |

## 6. Domain events

- Name: past tense, `<Aggregate><Happened>` e.g., `BookingConfirmed`. Package: `<module>.api` or its subpackage `<module>.api.events`; both carry `@NamedInterface("api")` so that `allowedDependencies = "<module>::api"` covers them.
- Payload: ids, timestamps, and minimal facts needed by consumers; never entities; never secrets or full personal data (consumers fetch via API when needed and authorized).
- Every event carries `eventId`, `occurredAt`, `schemaVersion`, `correlationId`. Breaking changes require a new event type or a new `schemaVersion` with dual publishing during migration.
- Consumers are idempotent and must tolerate reordering for unrelated aggregates; ordering per aggregate is by `occurredAt`/version where it matters.
- Event catalog is maintained in each module's `api` packages and catalogued in [EVENT_CATALOG.md](EVENT_CATALOG.md), which must be updated whenever an event is added or changed.

## 7. Ownership

Each module has a named owner (human team or role) recorded in `CODEOWNERS` once the repository is scaffolded. Changes crossing module boundaries require review from each affected owner.

## 8. Extraction readiness (not a plan to extract)

Modules are designed so that extraction is possible but is **not** performed without evidence: no shared tables, ID-only references, events for propagation, API facades that could become HTTP/gRPC. Candidates if ever needed: `notification`, `search`, `chat`. Extraction requires an ADR.
