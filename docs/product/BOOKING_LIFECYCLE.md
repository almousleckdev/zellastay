# Booking Lifecycle

Authoritative definition of booking states, transitions, timers, cancellation and refunds. Implementation lives in the `booking` module; any change to this file requires matching changes to the state machine and its tests.

## 1. Booking modes

- **Instant Booking** — customer pays; booking confirms automatically on successful payment.
- **Request to Book** — decided (ADR-13): **approve, then pay.** A request does not hold inventory. When the vendor approves, inventory is reserved temporarily while the customer pays; the payment deadline is configurable. No authorization-hold/capture is used.

The mode is a property/room-type/unit setting, gated by subscription entitlement.

## 2. States

| State | Meaning | Terminal |
|---|---|---|
| `REQUESTED` | Request-to-book awaiting vendor decision. **No inventory held**; availability is re-checked at approval. | no |
| `PENDING_PAYMENT` | Inventory held, awaiting successful payment before `payment_due_at`. | no |
| `CONFIRMED` | Paid and guaranteed. Inventory allocated. | no |
| `CHECKED_IN` | Guest arrived (vendor action or system rule). | no |
| `COMPLETED` | Stay ended. Reviews, loyalty accrual, settlement eligibility. | yes |
| `CANCELLED` | Cancelled; `cancelled_by` ∈ {CUSTOMER, VENDOR, ADMIN, SYSTEM}. | yes |
| `DECLINED` | Vendor declined a request. | yes |
| `EXPIRED` | Request unanswered, or payment window elapsed. | yes |
| `NO_SHOW` | Guest did not arrive; vendor-marked within allowed window. | yes |

## 3. Transition table (the state machine)

| From | Event | To | Guard / side effects |
|---|---|---|---|
| (none) | `createInstant` | `PENDING_PAYMENT` | Organization's settlement beneficiary is `VERIFIED` (ADR-24); quote valid; inventory hold acquired atomically; `payment_due_at = now + payment_window` |
| (none) | `createRequest` | `REQUESTED` | Quote valid; availability checked but not held; `respond_by = now + response_window` |
| `REQUESTED` | `vendorApprove` | `PENDING_PAYMENT` | Actor has booking permission; organization's settlement beneficiary is `VERIFIED` (ADR-24); **inventory hold acquired atomically** (same transaction); `payment_due_at = now + payment_window`; if inventory is no longer available the approval is rejected (`booking.unavailable`) and the booking stays `REQUESTED` for the vendor to decline; customer notified |
| `REQUESTED` | `vendorDecline` | `DECLINED` | Reason required |
| `REQUESTED` | `customerCancel` | `CANCELLED` | — |
| `REQUESTED` | `timeout` | `EXPIRED` | `respond_by` passed |
| `PENDING_PAYMENT` | `paymentSucceeded` | `CONFIRMED` | Payment amount/currency match quote; hold converted to allocation; ledger posting; outbox events |
| `PENDING_PAYMENT` | `paymentFailed` | `PENDING_PAYMENT` | Customer may retry until `payment_due_at` |
| `PENDING_PAYMENT` | `customerCancel` | `CANCELLED` | Hold released; any pending payment attempt cancelled/ignored |
| `PENDING_PAYMENT` | `timeout` | `EXPIRED` | Hold released; late payment success triggers auto-refund (see §6) |
| `CONFIRMED` | `customerCancel` | `CANCELLED` | Cancellation policy evaluated; refund workflow started |
| `CONFIRMED` | `vendorCancel` / `adminCancel` | `CANCELLED` | Full refund incl. service fee; vendor penalties are policy `[OD-7]` |
| `CONFIRMED` | `checkIn` | `CHECKED_IN` | Within check-in window |
| `CONFIRMED` | `markNoShow` | `NO_SHOW` | After check-in date + configured delay; refund per policy |
| `CHECKED_IN` | `checkOutOrAutoComplete` | `COMPLETED` | Automatic after check-out date + grace if not set manually |
| `CONFIRMED` | `autoComplete` | `COMPLETED` | Check-out passed and no check-in/no-show recorded (configurable) |

Any transition not in the table is rejected with a domain error. The state machine is a pure domain object (no I/O); persistence and events happen in the application service within one transaction. Each transition writes a `booking_status_history` row (from, to, actor, reason, timestamp).

## 4. Timers

All timers are platform parameters in `platformconfig` (per country, overridable per payment method). **No default is hardcoded**: the application refuses to start (or the feature to be enabled) if a required timer is unset. Initial values are product inputs `[OD-6a]`.

| Timer | Parameter | Scope |
|---|---|---|
| Vendor response deadline (`respond_by`) | `booking.request.response_window` | country |
| Payment deadline after instant booking | `booking.payment.window.instant.<method>` | country, method (mobile money needs longer than cards) |
| Payment deadline after vendor approval (`payment_due_at`) | `booking.payment.window.approved.<method>` | country, method |
| In-flight payment grace at deadline | `booking.payment.pending_grace` | country, method |
| Auto-complete grace after check-out | `booking.autocomplete.grace` | country |

### Safe expiry of reservations

A scheduled job selects overdue `PENDING_PAYMENT`/`REQUESTED` rows with `FOR UPDATE SKIP LOCKED` and applies `timeout` through the same state machine, so it is safe on multiple instances and idempotent. Rules:
1. Booking row is locked and its status re-checked inside the transaction; a booking that became `CONFIRMED` or `CANCELLED` meanwhile is skipped.
2. If a payment attempt is `PENDING` (e.g., customer approving a mobile-money prompt) and younger than `pending_grace`, expiry is deferred; after grace the booking expires and the inventory hold is released **in the same transaction** as the status change.
3. A payment that succeeds after expiry is never silently accepted: it triggers the automatic full refund path (§6.5) and the inventory is not re-reserved.
4. Search and availability treat holds past `expires_at` as released even if the job lags ([INVENTORY_ARCHITECTURE.md](../architecture/INVENTORY_ARCHITECTURE.md) §2).
5. Price binding (ADR-23): the price snapshot taken when the request was created is honored at approval and payment. If the price differs at approval time, the customer must explicitly accept the new price before payment can be initiated; payment initiation is rejected until acceptance, and the payment deadline is unaffected. How acceptance is represented in the state machine is a Phase 2 design task that must extend the transition table above.

## 5. Cancellation policies

A cancellation policy is a versioned, vendor-selected template from a platform-defined set (vendors choose; they do not invent arbitrary rules) `[OD-3]`. A policy is a list of tiers:

```
{ hoursBeforeCheckIn >= N  ->  refundPercentOfAccommodation: P }
```

with separate rules for the cleaning fee, security deposit (always returned unless a damage claim exists), customer service fee, and taxes. The booking stores a **snapshot** of the policy at booking time so later edits never alter existing bookings.

Computation (pure function in `booking` domain): `RefundAmount = f(policySnapshot, now, checkInInstantInPropertyTimeZone, paidBreakdown)`. Calculated in the property's time zone. Result is shown to the customer before they confirm cancellation and stored on the cancellation record.

Non-refundable rates are a policy with a single 0% tier.

## 6. Refund workflow

1. The cancellation transition commits with a `refund_due` amount. In the same transaction, booking calls `PaymentApi.requestRefund(...)`, which creates the refund record (`REQUESTED`); outbox event `BookingCancelled` is also written. (Payment never depends on booking; see [MODULAR_ARCHITECTURE.md](../architecture/MODULAR_ARCHITECTURE.md) §4.)
2. After commit, a payment worker calls the provider with an idempotency key derived from the refund id. Status: `REQUESTED → PENDING → SUCCEEDED | FAILED`.
3. Failed refunds retry with backoff; after the retry budget they enter an admin manual-handling queue. Money is never silently dropped.
4. Customer is notified at request and completion. The booking history shows refund state.
5. Late payment on an `EXPIRED`/`CANCELLED` booking (race with timeout) → automatic full refund, flagged for reconciliation review. The inventory is **not** re-reserved.
6. Refund destination: original payment method by default; mobile-money refund support must be validated per provider `[LEGAL]`.
7. Partial refunds (host goodwill, dispute resolution) require an admin or vendor-finance permission and are audit-logged.

Refunds are executed by the payment provider against the original collection (ADR-20). Who bears the cost of a vendor-funded refund (deduction from vendor settlement vs provider-supported reversal) depends on provider capabilities `[OD-18]` and the provider agreement `[LEGAL]`.

## 7. Guest (account-less) bookings

- Guest provides name, email and phone (phone required for mobile-money and SMS), accepts terms.
- The booking has `customer_id = null` and a `guest_contact` snapshot; access to view/cancel/chat uses a high-entropy booking access token delivered by email/SMS, stored hashed, scoped to that single booking, revocable.
- If the guest later registers with the same verified email/phone, bookings can be claimed after verification (explicit step).

## 8. Check-in information

Check-in instructions, access codes and exact address (for private homes) are released only when status ∈ {`CONFIRMED`, `CHECKED_IN`} and, optionally, within N hours of arrival. Before that, the public listing shows approximate location for private homes `[OD-8]`.

## 9. Events emitted (domain events, published via outbox)

`BookingRequested`, `BookingApproved`, `BookingDeclined`, `BookingPaymentDue`, `BookingExpiringSoon`, `BookingConfirmed`, `BookingCancelled`, `BookingExpired`, `BookingCheckedIn`, `BookingCompleted`, `BookingNoShow`, `BookingStayReminderDue`. Time-based events (`BookingPaymentDue`, `BookingExpiringSoon`, `BookingStayReminderDue`) are published by a booking-owned scheduled job, so notification never queries booking. Consumers: notification, chat (open/close permissions), loyalty (accrual), reporting, review, search. Inventory holds are released by booking calling `InventoryApi` within the transition transaction, not by event.

## 10. Idempotency and concurrency requirements

- Create-booking requires an `Idempotency-Key`; replays return the original result.
- The create transaction acquires inventory and inserts the booking atomically (see [INVENTORY_ARCHITECTURE.md](../architecture/INVENTORY_ARCHITECTURE.md)).
- State transitions use optimistic locking (`version` column) in addition to guards; concurrent conflicting transitions (e.g., vendor approves while customer cancels) resolve to exactly one winner, the other receives a domain conflict error.
- Payment callbacks are applied exactly once per payment id.
