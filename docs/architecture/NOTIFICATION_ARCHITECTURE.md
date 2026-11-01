# Notification Architecture

Owner module: `notification`. One centralized module for push, email, SMS and in-app notifications; WhatsApp is a future channel. **Invariant: a notification failure can never roll back, block, or alter the outcome of a booking, payment, or any other business transaction.**

## 1. Channels

| Channel | Purpose | Provider abstraction |
|---|---|---|
| In-app | Inbox and unread badge; WebSocket push when online | internal |
| Push | Mobile (FCM/APNs through one provider interface), web push optional later | `PushProvider` |
| Email | Transactional | `EmailProvider` |
| SMS | OTPs, critical alerts, fallback for users without data | `SmsProvider` (country-routed like payments) |
| WhatsApp (future) | Template messages | `WhatsAppProvider` (not built; model supports channel enum value) |

Providers are adapters selected by configuration per channel and country; each supports primary + fallback provider. Provider names are `[OD-20]`.

## 2. Pipeline

```
Business module ──(same tx)──► domain event in outbox
                                   │ after commit (async listener in notification)
                                   ▼
                       EventMapper: event → NotificationIntent(s)
                                   ▼
        Recipient resolution (identity API) → preferences + channel policy → locale resolution
                                   ▼
          notif_notification (dedupe_key unique) + notif_delivery rows per channel (PENDING)   [one tx]
                                   ▼
         Delivery worker (SKIP LOCKED, batches) → render template → provider.send
                                   ▼
                  SENT → (provider callback) DELIVERED | FAILED → retry/backoff → DEAD
```

- **Transactional outbox:** the business module's event is persisted atomically with its state change (Spring Modulith event publication registry). If notification is down, events remain unprocessed and are redelivered. The notification module's own `notif_delivery` rows form a second durable queue that isolates provider flakiness from event processing.
- **Asynchronous:** no controller or business service waits on a provider.
- **Idempotent:** `dedupe_key = hash(eventId, templateCode, recipient, channel)`; duplicate events create no duplicate deliveries.

## 3. Event → notification catalog (initial)

Recipients: **C** customer/guest, **V** vendor staff (by permission and property scope), **A** admin roles.

| Event | C | V | A | Default channels |
|---|---|---|---|---|
| `AccountRegistered`, `VerificationCodeRequested`, `PasswordResetRequested` | ● | | | SMS/email (high priority, no preference opt-out) |
| `BookingRequested` | ● (receipt) | ● (action needed) | | push, email, in-app |
| `BookingApproved` / `BookingDeclined` | ● | | | push, email, SMS, in-app |
| `BookingPaymentDue`, `BookingExpiringSoon` | ● | | | push, SMS |
| `PaymentSucceeded` / `PaymentFailed` | ● | | | push, email, in-app |
| `BookingConfirmed` | ● (voucher) | ● | | email, push, SMS, in-app |
| `BookingCancelled` | ● | ● | | email, push, in-app |
| `RefundRequested` / `RefundSucceeded` / `RefundFailed` | ● | | ● (failed/manual) | email, push, in-app |
| `StayReminder` (scheduled) | ● | ● | | push, SMS |
| `BookingCompleted` → `ReviewInvited` | ● | | | push, email |
| `ReviewPublished` | | ● | | in-app, email |
| `ChatMessageReceived` (offline/unread only) | ● | ● | | push, in-app; email digest |
| `VerificationSubmitted` | | | ● | in-app, email |
| `VerificationDecided` (approved/rejected + reasons) | | ● | | email, in-app, SMS |
| `SubscriptionRenewalDue`, `SubscriptionPastDue`, `SubscriptionSuspended` | | ● (owner/accountant) | ● | email, SMS, in-app |
| `DisputeOpened` / `DisputeResolved` | ● | ● | ● | email, in-app |
| `SettlementPaid` / `SettlementFailed` | | ● | ● (failed) | email, in-app |
| `PromoExpiring`, `LoyaltyPointsEarned`, `ReferralRewarded` | ● | | | push, email (marketing opt-in) |
| `ReconciliationExceptionRaised`, `ProviderCircuitOpen`, `OutboxBacklogHigh` | | | ● | in-app, email, ops pager |

Time-based triggers (`BookingPaymentDue`, `BookingExpiringSoon`, `StayReminder` = `BookingStayReminderDue`) are published by the booking module's scheduler and `ReviewInvited` by the review module; notification only consumes events and never queries other modules for due items. The catalog is data in code (mapper registry) plus templates in the database; adding a notification = new mapper + template + test.

## 4. Templates and localization

- `notif_template` identified by `code + channel + version`; translations per locale (`fr` default, `en`), editable in the Admin dashboard with preview and publish workflow (draft → published, versioned; deliveries record the template version used).
- Engine: a logic-less template syntax (e.g., Mustache-style) with a closed set of typed variables per template; no arbitrary expression execution (prevents template injection). Variables are escaped by channel (HTML for email, plain for SMS/push).
- Locale resolution order: recipient preference → booking/request locale (for guests) → country default → `fr`. Missing translation falls back along the chain and raises a monitoring warning; tests fail CI when a published template lacks `fr` or `en`.
- Money, dates and time zones are formatted by the locale/country formatter, in the property's time zone for stay-related times.
- SMS length/encoding awareness (GSM-7 vs UCS-2 segments) enforced in the renderer; French accents considered in cost estimates.

## 5. Preferences and consent

- Preferences per `(account, category, channel)`; categories: `TRANSACTIONAL` (cannot be disabled for required messages), `BOOKING_UPDATES`, `CHAT`, `MARKETING`, `ACCOUNT_SECURITY`.
- Marketing requires explicit opt-in with timestamped consent record `[LEGAL]`; every marketing email has unsubscribe; SMS marketing rules `[LEGAL]`.
- Quiet hours for non-urgent push/SMS, evaluated in recipient time zone (configurable).
- Guests (no account) receive transactional notifications on the contact given at booking.
- Channel policy per template: ordered fallback (e.g., push → SMS if not delivered within X minutes for time-critical items).

## 6. Delivery tracking and retries

- Status per delivery: `PENDING → SENDING → SENT → DELIVERED | FAILED → (retry) → DEAD`. Provider receipts/webhooks update to `DELIVERED`/`FAILED` (signature-verified, idempotent).
- Retry: exponential backoff with jitter; max attempts per channel and priority; permanent errors (invalid address, unregistered push token) go to `FAILED` without retry and trigger token cleanup/contact flags.
- Circuit breaker per provider; when open, deliveries wait or switch to fallback provider.
- `DEAD` deliveries are visible to admins with manual retry; alerting on dead-letter rate.
- Delivery workers claim rows with `FOR UPDATE SKIP LOCKED`, lease with `locked_until` to recover crashed workers; sends are made outside open DB transactions.
- Exactly-once send is not guaranteed by providers; design is at-least-once with dedupe keys passed as provider idempotency tokens where supported.

## 7. In-app and real-time

Inbox items persisted; unread counter cached in Redis and recomputed from DB on miss. When the user has an active WebSocket session, a message is pushed to `/user/queue/notifications`; otherwise push/other channels per policy.

## 8. Push token hygiene

Devices registered via identity (`ident_device`); notification asks identity for active tokens; invalid-token responses mark the device inactive.

## 9. Security and privacy

No sensitive content (full card details, OTP in logs) in stored params beyond what the template needs; OTP params expire/redacted after use; delivery payloads in logs are redacted; recipient contact data fetched at send time where possible instead of copied; retention for notification content `[LEGAL]`.

## 10. Observability and testing

Metrics: queue depth/age per channel, send latency, success/failure by provider, retry counts, dead-letter count, template fallback count. Tests: mapper unit tests; template completeness (fr/en) test; idempotency under duplicate events; provider failure leaves booking/payment untouched (integration test that kills the provider and asserts booking confirmed + delivery retrying); retry/backoff; preference enforcement.
