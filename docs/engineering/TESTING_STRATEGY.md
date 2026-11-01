# Testing Strategy

Test-first development: for each behavior, write a failing test, make it pass, refactor. A change is not complete until its tests pass in CI.

## 1. Test pyramid and tooling

| Level | Scope | Tooling (backend) | Speed |
|---|---|---|---|
| Unit | Domain objects, state machines, policy computation, mappers | JUnit 5, AssertJ, jqwik (property-based) | ms |
| Module/slice | One module with its real persistence, mocked other modules' APIs | Spring Boot test slices, Testcontainers PostgreSQL | seconds |
| Integration | Real PostgreSQL + Redis, multiple modules, events/outbox | Testcontainers, Spring Modulith `Scenario` API | seconds–minutes |
| Architecture | Module boundaries, layering, cycles, naming | `ApplicationModules.verify()`, ArchUnit | seconds |
| Contract | OpenAPI conformance; provider adapter contracts | OpenAPI diff, provider fixtures/sandbox | minutes |
| End-to-end | Critical journeys across real apps | Playwright (web), `integration_test` (Flutter) | minutes |
| Non-functional | Load, concurrency, security, accessibility | k6/Gatling, ZAP, axe | scheduled |

Databases in tests are real PostgreSQL (never H2) because correctness depends on constraints, locking and `daterange`/GiST features.

Implemented in Phase 0: architecture tests with fixtures that prove each ArchUnit rule detects its violation; documentation-to-code contract test for the module graph; Testcontainers integration tests (PostgreSQL 16, Redis 7) for Flyway, outbox atomicity/failure isolation/resubmission, health and metrics endpoints, mounted-secret configuration and fail-fast startup.

## 2. Mandatory test categories

1. **Domain rules:** every state-machine transition (allowed and forbidden) for booking, payment, refund, subscription, verification, conversation.
2. **Authorization:** for every endpoint — allowed role, denied role, unauthenticated, wrong audience, **cross-tenant** (org A vs org B), property-scope restriction, guest-token scope. Generated from the route catalog so a new endpoint without authorization tests fails CI.
3. **Concurrency** (real DB, parallel threads and multiple app instances): last-room race; duplicate booking requests with same idempotency key; payment success vs hold expiry race; vendor approve vs customer cancel; duplicate webhook delivery; simultaneous refund requests; promo code redemption limits.
4. **Idempotency and replay:** every idempotent endpoint and webhook handler.
5. **Money:** property-based tests for price/fee/tax/discount/refund arithmetic (no negative totals, rounding, sum invariants, currency exponents including XAF 0 and EUR 2).
6. **Outbox/events:** event persisted atomically with state change; listener failure leaves event pending and retried; duplicate delivery harmless; notification provider failure does not affect booking/payment.
7. **Security:** SQL injection and XSS probes on inputs, upload validation, rate-limit behavior, JWT tampering/expiry/audience, refresh token reuse, CORS.
8. **Migrations:** Flyway applies cleanly on an empty database and on a snapshot of the previous release schema; expand/contract compatibility check.
9. **Internationalization:** template completeness (fr/en), message bundle key parity, locale fallback, currency formatting, time-zone boundaries (stay dates around midnight and DST-free but zone-offset cases for other countries).
10a. **Error handling:** every error category's status/code/localization, validation shape, no leakage of stacks/SQL/secrets, request-id propagation to logs, code catalog completeness (fr + en); on clients: failure mapping, boundaries and states ([ERROR_HANDLING.md](ERROR_HANDLING.md)).
10b. **Platform UI (Flutter):** each `platform_ui` component tested on iOS and Android; screens pumped on both platforms assert no Material widgets on iOS and no Cupertino widgets on Android.
10c. **Frontend standards:** scanner tests with violating and clean fixtures (`infra/scripts/tests`); backend architecture rules proven against violating fixtures.
10d. **Media:** signature construction, intent → webhook → ready, oversize/invalid rejection with provider cleanup, replacement, deletion retry, orphan cleanup, private URL authorization and audit.
10. **Time:** all time-dependent logic tested with a controllable `Clock` (expiry, cancellation tiers, check-in windows).
11. **Reconciliation:** each outcome type; automatic vs manual resolution.
12. **Chat:** access matrix by booking status and role; multi-instance delivery; unread counter consistency; retention job.

## 3. Test data

- Built with explicit test data builders/factories in test sources; deterministic seeds; no shared mutable fixtures across tests; no production seed data used as test fixtures except reference configuration loaded the same way as in production.
- Randomness (property-based tests) logs its seed for reproduction.
- No real personal data, ever. Provider sandboxes use designated test credentials stored as CI secrets.

## 4. Frontend testing

- **Web:** Vitest + Testing Library (components, hooks), MSW for API mocking based on generated types, Playwright E2E (chromium + webkit, mobile viewport), axe accessibility, Storybook interaction tests, Lighthouse CI budgets, visual regression for design-system components.
- **Mobile:** unit tests for notifiers and repositories using provider overrides, widget and golden tests, `integration_test` flows on emulators in CI for the critical path, accessibility checks (`meetsGuideline`).
- Frontend tests do not re-test backend rules; they verify rendering of server decisions and error handling.

## 5. Critical journeys (E2E, must always be green on main)

1. Visitor searches, opens property, books instantly with mobile money (provider simulator), receives confirmation.
2. Guest books without an account and cancels via tokenized link; refund preview matches policy.
3. Request-to-book: customer requests, vendor approves, customer pays, booking confirmed; decline and timeout variants.
4. Vendor registers, uploads documents, admin rejects with reasons, vendor resubmits, admin approves, vendor publishes, property appears in search; unapproved property never appears.
5. Subscription purchase, renewal failure → past due → suspended; confirmed bookings unaffected.
6. Chat between customer and vendor on a confirmed booking; no access after read-only transition.
7. Refund lifecycle including provider failure → retry → manual queue.

## 6. Quality gates

| Gate | Threshold |
|---|---|
| Backend line coverage (domain + application) | ≥ 85% (meaningful assertions reviewed; coverage is a floor, not a goal) |
| Mutation testing on `booking`, `inventory`, `payment`, `pricing` domain packages | Score target set at Phase 2 start `[OD-14]` |
| Web/mobile coverage | ≥ 80% on logic-bearing code |
| Flaky test policy | Quarantine within 24 h with ticket; no retries to hide failures in critical suites |
| Static analysis, formatting, lint, type-check | Zero violations |
| Dependency/container/secret scanning | No unresolved high/critical findings |
| Architecture tests | Pass |
| OpenAPI breaking-change check | Pass |

## 7. Non-functional testing

- Load/performance tests (search, availability, booking creation, WebSocket connections) in a production-like environment before each major phase; targets from `[OD-14]`.
- Chaos-style tests in staging: kill app instance mid-booking, drop Redis, delay provider, partition DB briefly; verify invariants hold (no double bookings, no duplicate charges, no lost events).
- Security testing: DAST (ZAP) on staging, annual third-party penetration test and one before public launch.
- Backup/restore drills.

## 8. Reporting unexecuted checks

If any required check cannot be run (environment limits, missing credentials, time), the author states exactly which checks were skipped and why in the PR/task report. Skipped checks are never described as passed.
