# Payment Architecture

Owner module: `payment`. Payment provider capabilities (hold/capture, refunds, webhooks, recurring debit, settlement reports, sandbox) for Airtel Money, Moov Money and the card processor are **unverified**. Every capability below is therefore a *required capability* to be confirmed per provider before implementation `[LEGAL]`. Funds flow is decided (ADR-20): a licensed marketplace payment provider collects and settles to vendors; Zella Stay does not custody funds. Provider selection is still open `[OD-18]`; research is in [PAYMENT_PROVIDER_RESEARCH.md](PAYMENT_PROVIDER_RESEARCH.md). **No live transaction before legal validation L-1 is closed.**

## 1. Goals

Correct money movement under retries, duplicate callbacks, timeouts and partial failures; auditable ledger; country-specific provider routing; no card data on our servers.

## 2. Provider abstraction

```java
interface PaymentProvider {            // one adapter per provider, in payment.infrastructure.provider.*
    ProviderKey key();
    Set<PaymentMethod> supportedMethods();
    InitiationResult initiate(InitiationRequest r);       // idempotent by r.idempotencyKey
    StatusResult fetchStatus(ProviderReference ref);       // used for polling & reconciliation
    RefundResult refund(RefundRequest r);                  // idempotent
    WebhookEvent parseAndVerify(RawWebhook raw);           // signature + replay checks
    Optional<SettlementReport> fetchSettlement(LocalDate d); // for reconciliation, if supported
    Capabilities capabilities();                           // holdCapture, partialRefund, recurring, ...
}
```

- A `PaymentRouter` selects the provider from the payment module's own method configuration (`pay_method`, `pay_market_method`; ADR-36) using the **property's market**, the booking currency, the method and the payer scope. Adding a country = adding configuration and, if needed, a new adapter class. Business code never names a provider.
- **Cross-border bookings (ADR-37):** the property's country governs the booking currency and the method set. International cards are supported for cross-border bookings. A mobile-money method is offered across borders only if the provider integration declares that exact payer-country and market pair; otherwise it is domestic-only. A method available in one country is never assumed to work in another.
- **Currency:** the customer is charged in the property's currency. There is no automatic conversion by Zella Stay; a card issuer's own conversion is disclosed to the customer `[LEGAL]` (L-10, L-19). `XAF` and `XOF` are distinct currencies and are never mixed within a payment or ledger transaction. Refunds use the original currency and the original method.
- **Ledger and reconciliation** are per currency; provider reports are reconciled per provider and market. Open: how the payer country is determined and who bears cross-border refund and exchange losses (OD-34).
- **Commission (ADR-35):** if a market enables vendor commission, it is delivered through the provider's split and settlement mechanism and mirrored in the ledger; the booking snapshots the rule applied. Never by Zella Stay holding funds (ADR-20).
- **Legal gate per country:** L-1 must be closed for a country before any live transaction in that market. Provider capabilities are researched per country (OD-18).
- Adapters translate provider errors into a small internal failure taxonomy (`DECLINED`, `INSUFFICIENT_FUNDS`, `TIMEOUT_UNKNOWN`, `INVALID_ACCOUNT`, `PROVIDER_UNAVAILABLE`, …); `TIMEOUT_UNKNOWN` never marks a payment failed — it stays `PENDING` until resolved by webhook/poll.
- Every adapter call has timeout, bounded retry (only for idempotent calls), and a circuit breaker.
- Contract tests per adapter run against provider sandboxes in a nightly pipeline and against recorded fixtures in CI.

## 3. Payment methods at launch (Gabon; other markets researched before their activation)

| Method | Interaction | Notes |
|---|---|---|
| Airtel Money | Customer enters MSISDN; provider prompts customer on handset; result via webhook (and/or polling) | Window needs more time than cards (USSD) |
| Moov Money | Same pattern | |
| Visa / Mastercard | Provider-hosted fields or redirect; 3-D Secure | Tokenization; server stores only token/last4/brand |

Provider selection is deliberately **not made**: candidates and verified/unverified capabilities are tracked in [PAYMENT_PROVIDER_RESEARCH.md](PAYMENT_PROVIDER_RESEARCH.md) `[OD-18]`.

## 4. Payment state machine

```
INITIATED ──initiate ok──► PENDING ──provider success──► SUCCEEDED
    │                         │ └──provider failure──► FAILED
    └──initiate rejected──► FAILED          └──timeout/expiry──► EXPIRED
```

- Terminal: `SUCCEEDED`, `FAILED`, `EXPIRED`. Terminal states are immutable, except that a late `SUCCEEDED` for an `EXPIRED` payment is recorded as an **anomaly**: payment is marked `SUCCEEDED`, and the owning flow decides (booking: auto-refund per [BOOKING_LIFECYCLE.md](../product/BOOKING_LIFECYCLE.md) §6).
- One booking may have many payment attempts (retries); at most one may reach `SUCCEEDED` for the same booking amount (enforced by a unique partial index on `(reference_type, reference_id) WHERE status = 'SUCCEEDED'`). A second success is an anomaly → automatic refund.

## 5. Idempotency

Layers:
1. **Client → us:** `Idempotency-Key` on `POST …/payments`. Stored with request hash and response; replay returns stored response. Unique on `(actor_key, key)`.
2. **Us → provider:** deterministic provider idempotency/transaction reference derived from `payment.id` (and `refund.id` for refunds). Retries reuse it. If a provider lacks idempotency support, we first persist `INITIATED`, call once, and on ambiguous outcome never re-initiate — only `fetchStatus`.
3. **Provider → us (webhooks):** unique `(provider_key, provider_event_id)`; if the provider has no event id, use a hash of canonical payload + provider reference + status. Handlers are state-machine guarded, so duplicates and out-of-order events are no-ops.
4. **Downstream effects:** `PaymentSucceeded` is published once via outbox when the state transitions; consumers are idempotent by event id.

## 6. Payment flow (booking)

1. The customer initiates payment on a `PENDING_PAYMENT` booking (`POST /bookings/{id}/payments`). Booking validates status and `payment_due_at`, then in its transaction calls `PaymentApi.createPayment(reference=booking:{id}, amount, currency, method)`, which persists `INITIATED`. The inventory hold already exists (taken at booking creation for instant bookings, at vendor approval for requests).
2. After commit, payment calls the provider (`initiate`). Result persisted → `PENDING` (or `FAILED`). The client receives a `paymentId` and awaits via WebSocket `/user/queue/payment-updates` or polling `GET /payments/{id}`.
3. Webhook arrives → verify signature → dedupe → **re-validate** by `fetchStatus` for critical transitions (do not trust callback body alone) → verify amount and currency equal the stored payment → transition → ledger postings → outbox `PaymentSucceeded`.
4. Booking listener confirms the booking (idempotent, guarded by state).
5. A scheduled job polls `PENDING` payments older than a threshold (provider callbacks can be lost) and also expires them after the payment window.

Provider calls are never made inside an open DB transaction holding row locks.

## 7. Refunds

- Created by calling `PaymentApi.requestRefund(paymentId, amount, reason, requestedBy, idempotencyKey)` from booking cancellation (same transaction), support dispute resolution, or admin action.
- Invariants: sum of non-failed refunds ≤ captured amount; refund currency = payment currency; amounts validated server-side.
- Execution: after commit a worker calls `provider.refund` with idempotency key derived from `refund.id`. States `REQUESTED → PENDING → SUCCEEDED | FAILED`. Failures retry with exponential backoff and jitter; after the retry budget → `MANUAL` (admin queue) — never silently dropped.
- If the original method cannot be refunded (provider limitation), the manual path pays out through an alternative channel with four-eyes approval `[LEGAL]`.
- Ledger entries are posted on `SUCCEEDED` only; pending refunds show as liabilities in reports.
- Notifications on request and completion; failures alert operations.

## 8. Ledger (accounting mirror)

Double-entry, append-only, per currency. It **mirrors** what the provider holds and owes; Zella Stay holds no customer funds (ADR-20). Accounts (illustrative): `PROVIDER_COLLECTED:<provider>` (receivable from provider), `VENDOR_SETTLEMENT_DUE:<org>`, `PLATFORM_SERVICE_FEE_REVENUE`, `SUBSCRIPTION_REVENUE`, `REFUND_DUE`, `PROVIDER_FEES_EXPENSE`, `TAX_PAYABLE:<tax>`. Postings come from explicit policies (`BookingPaidPolicy`, `RefundPolicy`, `SettlementPolicy`). Every ledger transaction balances (code check plus nightly verification job). Reports read the ledger, not payment tables. Provider settlement reports are the external source of truth the ledger is reconciled against.

## 9. Reconciliation

- **Daily automated run** per provider and day: obtain settlement/transaction report (API or file import) → match to `pay_payment`/`pay_refund` by provider reference, amount, currency, status → emit `pay_reconciliation_item` outcomes: `MATCHED`, `MISSING_INTERNAL` (provider has it, we do not), `MISSING_PROVIDER`, `AMOUNT_MISMATCH`, `STATUS_MISMATCH`.
- Automatic resolutions only for safe cases (e.g., stale `PENDING` that provider shows `SUCCEEDED` ⇒ apply the same state-machine transition). Everything else goes to the admin queue with required resolution notes (audited).
- Intraday: status polling for stuck `PENDING`.
- Metrics and alerts: unmatched count/value, age of oldest exception, PENDING older than N minutes.
- Fee reconciliation (provider fees vs expected) and settlement-to-bank reconciliation are finance-process items covered by provider agreement terms `[LEGAL]`.

## 10. Vendor settlements (provider-executed)

- Vendors are registered with the provider as sub-merchants/beneficiaries; the provider performs split and settlement. Zella Stay requests/schedules settlement through provider capabilities (e.g., release after check-in) and records provider settlement events as `pay_settlement` rows (`SCHEDULED → PROCESSING → PAID | FAILED`).
- `PaymentApi.isBeneficiaryVerified(organizationId)` is the single source for ADR-24; booking creation (instant) and vendor approval call it server-side and reject with a domain error when it is false.
- If a provider cannot split at collection time or hold funds until check-in, that is a capability gap to be raised against the provider shortlist; it is not solved by custody.
- Settlement failures and mismatches surface in the Admin dashboard; vendor statements are generated from the ledger and provider settlement reports.

## 11. Subscription payments

Subscription charges use the same `PaymentApi` with `reference=subscription_invoice:{id}`. If a provider does not support merchant-initiated recurring debit, renewal is invoice + reminder + customer-approved payment `[OD-5]`. Payment module is unaware of plans.

## 12. Security and PCI

No PAN/CVV on our systems; tokens only; webhook secrets per environment; provider credentials in secret manager; logs redact; manual financial actions require specific admin permissions and audit entries; four-eyes approval above configurable thresholds.

## 13. Observability

Metrics: initiation latency/success rate per provider and method, webhook lag, PENDING age distribution, refund success rate and time-to-complete, reconciliation exception count/value. Alerts on provider error-rate spikes (circuit-breaker open), ledger imbalance, duplicate-success anomalies, webhook signature failures.

## 14. Testing requirements

Property/unit tests for state machine and refund arithmetic; integration tests with provider fakes simulating: timeout-unknown, duplicate webhook, out-of-order webhook, late success after expiry, double success, partial refund; concurrency test of two simultaneous payment successes for one booking; reconciliation tests per outcome type.
