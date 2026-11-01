# Observability

Goals: detect and diagnose problems before users report them; prove business invariants hold in production; support audits and incident reviews. Vendor-neutral instrumentation (OpenTelemetry, Micrometer) with backend tooling chosen later `[OD-15]`.

## 1. Pillars

### Logs
- Structured JSON to stdout; fields: `timestamp`, `level`, `service`, `env`, `version`, `module`, `traceId`, `spanId`, `correlationId`, `requestId`, `actorType`, `actorId` (hashed/opaque), `organizationId`, `country`, event/message.
- **Never log:** passwords, tokens, OTPs, card data, document contents, message bodies, full phone/email (masked), webhook secrets. A logging test scans for forbidden patterns; PII masking utilities are mandatory.
- Levels: ERROR = action needed; WARN = degraded/recoverable; INFO = business milestones (booking confirmed, payment succeeded); DEBUG off in production by default.
- Retention per class `[LEGAL]`; access controlled and audited.

### Metrics (Micrometer → Prometheus-compatible)
Standard: JVM, HTTP server (RED), DB pool, Redis, executors, Kafka (when present).
Business and reliability metrics, tagged by `module`, `country`, `provider` (low cardinality only; never user ids):

| Area | Metrics |
|---|---|
| Booking | created/confirmed/expired/cancelled counts by mode, conversion funnel steps, creation latency, hold conflicts (`unavailable`), expired holds released, state-transition errors |
| Inventory | lock wait time, `lock_timeout` retries, availability rows near zero, hold-expiry job lag |
| Payment | initiation latency/success by provider and method, PENDING age histogram, webhook lag and signature failures, duplicate/late success anomalies, refund success/latency/manual queue size, reconciliation unmatched count/value, ledger imbalance (must be 0) |
| Outbox | unprocessed publications count/age, listener failures, redelivery count |
| Notification | queue depth/age per channel, send success by provider, retry/dead counts, template fallback count |
| Chat | active WebSocket sessions, message send latency, delivery lag, reconnect rate, Redis bridge errors, unread-counter repairs |
| Subscription | state counts, renewal failures, entitlement denials |
| Search | latency, zero-result rate, index lag |
| Auth | login success/failure, lockouts, OTP sends per country (fraud signal), refresh reuse detections |
| Onboarding | queue size, time-in-queue (SLA), approval/rejection rates |

### Traces
OpenTelemetry auto + manual spans: HTTP → application service → DB → provider calls; context propagated through events (correlation id stored in the outbox event) and WebSocket frames. Sampling: head-based baseline plus tail-based retention for errors and slow traces. Frontends propagate `traceparent` on API calls; web RUM and mobile crash/performance reporting are privacy-reviewed and consent-gated `[LEGAL]`.

## 2. SLIs, SLOs and alerts

Targets set in `[OD-14]` before launch; proposed SLIs:
- Availability of booking creation and payment initiation endpoints.
- Latency p95 of search, quote, booking creation.
- Payment confirmation time (initiation → `SUCCEEDED`) per method.
- Notification delivery time for transactional messages.
- Freshness of search read model (event lag).
- Chat delivery latency.

Alerting rules (page vs ticket):
- **Page:** booking error rate spike; payment provider circuit open; webhook signature failures spike; ledger imbalance; outbox backlog age > threshold; DB failover/replica lag; SLO burn rate fast; reconciliation unmatched value > threshold; any detected double booking or duplicate successful payment for one booking.
- **Ticket:** notification dead-letter growth; slow verification queue; elevated zero-result searches; certificate/secret expiry within N days; dependency vulnerabilities.

## 3. Business invariant monitors (continuous checks)

Scheduled queries that must return zero, alerting otherwise:
- Overlapping active reservations for any unit; availability counters outside `[0, total]`.
- Bookings `CONFIRMED` without a `SUCCEEDED` payment (or with amount mismatch).
- Payments `SUCCEEDED` whose booking is `EXPIRED/CANCELLED` without an open refund.
- Ledger transactions where debits ≠ credits.
- `PENDING` payments older than the provider window.
- Holds expired > N minutes but not released.
- Outbox publications unprocessed beyond N minutes.
- Cross-module dangling references (nightly integrity job).
- Organizations with ≠ 1 active owner; > 1 live subscription.

## 4. Dashboards

Executive/business (GMV, bookings, revenue, conversion), Operations (queues, SLOs, provider health), Payments (funnel, reconciliation), Inventory/Booking health, Notifications, Chat, Security (auth anomalies, rate-limit hits, admin actions), per-country views.

## 5. Health and diagnostics

Liveness/readiness endpoints; readiness reflects DB (and critical dependencies appropriately; Redis loss must not make the app unready). Actuator endpoints exposed only on an internal management port. Version/build info endpoint (non-sensitive). Feature flag state visible to admins. Runbooks linked from each alert.

## 6. Audit vs observability

Audit log ([SECURITY_ARCHITECTURE.md](../architecture/SECURITY_ARCHITECTURE.md) §8) is a business record in PostgreSQL, not a log stream; observability data is operational and retention-limited. Neither substitutes for the other.

## 7. Testing observability

Tests assert that key metrics/log fields exist for critical flows; synthetic probes (booking in staging with provider simulator, search probe, chat probe) run continuously in staging and a safe read-only variant in production.
