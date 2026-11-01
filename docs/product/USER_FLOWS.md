# User Flows

Text flows (steps and failure paths). Diagrams may be added as Mermaid when flows are finalized. Booking states are defined in [BOOKING_LIFECYCLE.md](BOOKING_LIFECYCLE.md).

## 1. Customer: search and instant booking (account or guest)

1. Customer searches destination + dates + guests (web or mobile). Results show only eligible listings with total price for stay.
2. Opens a property; sees gallery, amenities, policies, reviews, map, nearby attractions.
3. Selects room type/unit; system requests a **quote** (server computes accommodation, taxes, fees, service fee, discounts; quote has `expires_at`).
4. Checkout: signs in, registers, or continues as guest (name, email, phone).
5. Applies promo code / loyalty points (if enabled). Quote recomputed.
6. Confirms → `POST /bookings` with `Idempotency-Key`. Inventory hold acquired → `PENDING_PAYMENT`.
   - Failure: no availability → friendly message with alternatives; quote expired → re-quote.
7. Chooses payment method (Airtel Money, Moov Money, card).
   - Mobile money: enters phone number, receives provider prompt/USSD push, approves; app shows pending state and listens for result (WebSocket, falling back to polling).
   - Card: redirected/embedded provider flow with 3-D Secure where required.
8. Payment succeeds → `CONFIRMED`; confirmation screen, email/SMS/push, booking appears in history; chat opens.
   - Failure/timeout: customer can retry or choose another method until `payment_due_at`, then `EXPIRED`.

## 2. Customer: request to book

1–5 as above for a property configured as Request to Book.
6. Submits request with optional message → `REQUESTED`; no inventory is held yet; chat opens for this request.
7. Vendor approves → inventory is reserved for the configured payment window → customer notified with the payment deadline → `PENDING_PAYMENT` → pays as in §1.7 (if the dates were taken in the meantime, approval is refused and the vendor is told). Vendor declines or does not respond → `DECLINED`/`EXPIRED`, customer offered similar properties. If the payment deadline passes, the booking expires and the reservation is released.

## 3. Customer: cancel and refund

1. Opens booking → Cancel. App shows the refund amount computed from the policy snapshot, with explanation.
2. Confirms. Booking → `CANCELLED`; refund workflow starts; notification at request and completion.
3. Refund failure → admin queue; customer informed of expected resolution via support.

## 4. Customer: stay, chat, review

1. Before arrival: check-in instructions released; chat with vendor.
2. After check-out: booking `COMPLETED`; review invitation after configured delay; loyalty points accrue.
3. Leaves rating + text; vendor may publicly respond; admin may moderate on report.

## 5. Visitor → vendor: onboarding

1. On public website selects "List your property"; chooses Individual or Business.
2. Creates account (email + phone verification).
3. Completes profile and uploads required documents (types depend on country configuration).
4. Creates first property draft (type, address/geolocation, photos, amenities, rules, inventory structure, pricing basics, cancellation policy, booking mode).
5. Submits for review → organization and property `PENDING_REVIEW`. Nothing is public.
6. Admin reviews: approve → vendor notified, selects subscription plan, publishes the listing; reject → reasons per item, vendor corrects and resubmits (new submission version; history preserved).
7. Subsequent properties go through property-level review (organization remains approved).

## 6. Vendor: daily operations

- Receptionist: view arrivals/departures, check guests in, message guests, mark no-show (property-scoped).
- Manager: manage calendar, rates, approve/decline requests, respond to reviews, create promotions (if entitled).
- Accountant: view financial reports, settlements, invoices, export.
- Administrator: manage staff, properties, settings.
- Owner: all of the above plus subscription, ownership transfer, closing the organization.

## 7. Vendor: subscription

1. Select plan (monthly/annual) → pay via payment abstraction → `ACTIVE`.
2. Renewal reminder → payment → renewed; failure → `PAST_DUE` with banner → grace → `SUSPENDED` (rules in [BUSINESS_MODEL.md](BUSINESS_MODEL.md)).
3. Upgrade/downgrade/cancel from dashboard.

## 8. Admin: verification and operations

- Verification reviewer: claim item from queue, inspect documents (time-limited signed URLs), approve or reject with reason codes and notes.
- Support agent: view customer/vendor/booking (read-mostly, access logged), mediate disputes, trigger permitted actions.
- Finance admin: configure fees and plans, review reconciliation exceptions, approve large refunds, manage settlements.
- Super admin: manage admin accounts and roles, platform configuration, audit access.

## 9. Dispute

1. Customer or vendor opens a dispute against a booking (reason, description, attachments).
2. Counterparty is notified and may respond within the allowed time.
3. Support agent reviews, may request more evidence, resolves: dismiss / full refund / partial refund / vendor penalty. Decision is recorded; refunds follow the standard workflow.

## 10. Notifications (cross-cutting)

Every flow above emits domain events; the notification module decides channels per template and user preference. Failure of any channel never changes booking or payment state. See [NOTIFICATION_ARCHITECTURE.md](../architecture/NOTIFICATION_ARCHITECTURE.md).
