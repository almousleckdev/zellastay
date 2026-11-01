# Product Requirements Document — Zella Stay

Status: Draft for approval. Items tagged `[OD-n]` are open decisions listed in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md). Items tagged `[LEGAL]` need local legal or financial-provider validation before implementation.

## 1. Vision

Zella Stay is an international accommodation marketplace comparable in scope to Booking.com, Airbnb, Trip.com and Expedia. It launches in **Gabon** and is architected so that adding a country, language, currency, tax rule or payment provider is a configuration and adapter task, not a restructuring.

## 2. Goals and non-goals

Goals
- Give travellers in Gabon a trustworthy way to find, book and pay for accommodation using local payment methods.
- Give hotels, hotel groups, property managers and individual hosts one tool to onboard, manage inventory, communicate with guests and see finances.
- Give the platform operator verification, moderation, configuration, support and financial oversight tools.
- Remain correct under concurrency: no double bookings, no double charges, no lost notifications.

Non-goals (initial release)
- Flights, car rental, tours, packages (data model must not preclude them, but nothing is built).
- Per-booking commission on vendors (see [BUSINESS_MODEL.md](BUSINESS_MODEL.md)).
- Microservices.
- WhatsApp delivery (designed for, not built).

## 3. Users and applications

| Actor | Application | Description |
|---|---|---|
| Visitor | Public website | Browses and searches without an account; may book as guest. |
| Customer | Mobile app, public website | Registered traveller. Bookings, wishlists, chat, loyalty, reviews. |
| Vendor user | Vendor dashboard | Member of a vendor organization: Owner, Administrator, Manager, Receptionist, Accountant. |
| Prospective vendor | Public website | Self-registers (individual or business) and submits documents and properties. |
| Platform admin | Admin dashboard | Verification reviewer, support agent, finance admin, super admin. |

Four independent frontends (customer mobile, public website, vendor dashboard, admin dashboard) share one backend (modular monolith). See [SYSTEM_ARCHITECTURE.md](../architecture/SYSTEM_ARCHITECTURE.md).

## 4. Functional requirements

Requirement IDs are stable and referenced from tests and other documents. Priority: **M** = launch (Gabon MVP), **S** = soon after launch, **F** = future.

### 4.1 Accounts and identity
- FR-ID-1 (M) Customer registration and login by email or phone; email/phone verification.
- FR-ID-2 (M) Guest checkout without an account; guest accesses the booking through a scoped access token.
- FR-ID-3 (M) Vendor users belong to exactly one organization membership per organization they join; a person may belong to several organizations. `[OD-9]`
- FR-ID-4 (M) Strict separation of customer, vendor and admin authorization (see [SECURITY_ARCHITECTURE.md](../architecture/SECURITY_ARCHITECTURE.md)).
- FR-ID-5 (S) Social login. (F) Passkeys.

### 4.2 Vendor onboarding and verification
- FR-VO-1 (M) Self-registration on the public website as an individual or a business.
- FR-VO-2 (M) Identity documents (individual) or business documents (business) are uploaded and stored privately.
- FR-VO-3 (M) At least one property submission per organization before review.
- FR-VO-4 (M) Mandatory administrator approval; an unapproved organization or property is never publicly visible.
- FR-VO-5 (M) Rejection requires a reason; the vendor can correct and resubmit; history of submissions is kept.
- FR-VO-6 (M) Required document types per country are configuration, not code. `[LEGAL]` for the actual Gabon list.

### 4.3 Vendor organization management
- FR-VM-1 (M) One organization manages many properties and many staff accounts.
- FR-VM-2 (M) Roles: Owner, Administrator, Manager, Receptionist, Accountant, with a permission matrix defined in [SECURITY_ARCHITECTURE.md](../architecture/SECURITY_ARCHITECTURE.md).
- FR-VM-3 (M) Per-property access scoping for staff (all properties or a named subset).
- FR-VM-4 (M) Staff invitations, deactivation, and audit of role changes.

### 4.4 Catalog
- FR-CT-1 (M) Property types: hotel, resort, apartment, serviced apartment, private home, holiday home, villa, guesthouse, hostel, bed and breakfast. The list is extensible data.
- FR-CT-2 (M) Photo galleries (M) and videos (S) with ordering and captions in each supported language.
- FR-CT-3 (M) Amenities from a managed catalog, grouped by category, translatable.
- FR-CT-4 (M) Geolocation, map display, nearby attractions (platform-curated points of interest).
- FR-CT-5 (M) House rules, check-in/check-out times, check-in instructions (released to the guest only after confirmation).

### 4.5 Inventory and pricing
- FR-IN-1 (M) Hotel-style: room types with multiple physical rooms per type, availability calendar, seasonal pricing.
- FR-IN-2 (M) Private-style: entire property or individual units, host-managed calendar, cleaning fee, security deposit where applicable.
- FR-IN-3 (M) Double bookings are impossible: enforced by database-level concurrency controls and transactional reservation ([INVENTORY_ARCHITECTURE.md](../architecture/INVENTORY_ARCHITECTURE.md)).
- FR-IN-4 (M) Rate rules: base rate, seasonal overrides, weekday/weekend, min/max stay, per-guest supplements. Final scope `[OD-11]`.
- FR-IN-5 (S) Channel-manager/iCal sync for hosts who list elsewhere. `[OD-12]`

### 4.6 Search and discovery
- FR-SR-1 (M) Search by destination, dates, guests; filters for type, price, amenities, rating, instant booking.
- FR-SR-2 (M) Map-based search. (S) Sorting by distance/popularity.
- FR-SR-3 (M) Favorites and wishlists (account required).
- FR-SR-4 (M) Only approved, published, subscription-eligible properties appear in results.

### 4.7 Booking
- FR-BK-1 (M) Instant Booking and Request to Book, set per property (or per room type/unit). Lifecycle in [BOOKING_LIFECYCLE.md](BOOKING_LIFECYCLE.md).
- FR-BK-2 (M) Booking with or without account.
- FR-BK-3 (M) Cancellation policies (per property), booking expiry, refunds, booking history.
- FR-BK-4 (M) Price breakdown shown before payment: accommodation, taxes, vendor fees (cleaning), customer service fee, discounts.
- FR-BK-5 (M) Price quoted at booking start is guaranteed for the hold duration.

### 4.8 Payments
- FR-PY-1 (M) Airtel Money, Moov Money, Visa, Mastercard in Gabon through a provider abstraction ([PAYMENT_ARCHITECTURE.md](../architecture/PAYMENT_ARCHITECTURE.md)).
- FR-PY-2 (M) Idempotent payment initiation and webhook handling; transaction tracking; reconciliation; refunds.
- FR-PY-3 (M) Payment-provider routing per country and currency.
- FR-PY-5 (M) Vendors are onboarded as settlement beneficiaries with the payment provider (provider-side KYC). A vendor organization cannot accept live bookings until its beneficiary verification succeeds (ADR-24).
- FR-PY-4 (M) Guest funds are collected, and vendors settled, by a licensed marketplace payment provider; Zella Stay does not custody funds (ADR-20). Mandatory Gabon legal and provider validation before any live transaction `[LEGAL]` (L-1).

### 4.9 Subscriptions and revenue
- FR-SB-1 (M) Vendor subscriptions, monthly and annual; tiers Basic, Professional, Enterprise; admin-configurable plans, prices, limits and features.
- FR-SB-2 (M) Customer service fee on successful bookings; configurable by country.
- FR-SB-3 (M) Subscription state controls vendor capabilities (see [BUSINESS_MODEL.md](BUSINESS_MODEL.md)).
- FR-SB-4 (M) No per-booking vendor commission.

### 4.10 Chat
- FR-CH-1 (M) Real-time customer/vendor chat tied to a booking or booking request.
- FR-CH-2 (M) Text and image attachments, delivery status, read receipts, unread counters, history.
- FR-CH-3 (M) Vendors may contact customers about their reservations.
- FR-CH-4 (M) Access follows booking status and authorization ([CHAT_ARCHITECTURE.md](../architecture/CHAT_ARCHITECTURE.md)).

### 4.11 Notifications
- FR-NT-1 (M) Push, email, SMS, in-app. (F) WhatsApp.
- FR-NT-2 (M) French and English templates, user preferences, delivery tracking, retries.
- FR-NT-3 (M) Transactional outbox, asynchronous delivery; provider failure never rolls back a booking or payment.

### 4.12 Loyalty and promotions
- FR-LY-1 (S) Points, rewards, discounts, promo codes, referral rewards, seasonal campaigns, reward history.
- FR-LY-2 (S) Vendors create campaigns within platform rules. Launch scope `[OD-13]`

### 4.13 Reviews, support, disputes
- FR-RV-1 (M) Reviews and ratings only from customers with a completed booking.
- FR-SP-1 (M) Customer support tickets; disputes and complaints with evidence, status, and admin resolution.

### 4.14 Reporting
- FR-RP-1 (M) Vendor financial reports (bookings, revenue, refunds, settlements, taxes, exports).
- FR-RP-2 (M) Admin platform reports (GMV, service-fee revenue, subscription revenue, verification queue, reconciliation exceptions).

### 4.15 Administration
- FR-AD-1 (M) Verification queue, moderation, user/org suspension, plan and fee configuration, country/currency/tax configuration, template management, audit log viewer, reconciliation and refund oversight.

## 5. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Correctness: zero double bookings and zero duplicate charges under concurrent load (verified by automated concurrency tests). |
| NFR-2 | Availability target and RTO/RPO are set before launch `[OD-14]`. |
| NFR-3 | Performance targets (search p95, booking p95) set before launch `[OD-14]`; measured in [OBSERVABILITY.md](../engineering/OBSERVABILITY.md). |
| NFR-4 | Mobile-network resilience: Gabon connectivity may be slow or intermittent; payloads small, images responsive, retries safe through idempotency. |
| NFR-5 | Security by default; OWASP ASVS Level 2 as the target baseline. |
| NFR-6 | Privacy: data minimization, purpose limitation, retention rules, deletion/export workflows `[LEGAL]`. |
| NFR-7 | Accessibility: WCAG 2.2 AA for web, platform accessibility guidelines for mobile. |
| NFR-8 | Internationalization: no user-visible string, currency format, date format or time zone is hardcoded. |
| NFR-9 | Auditability: privileged and financial actions are recorded immutably. |
| NFR-10 | Operability: every module emits structured logs, metrics and traces. |

## 6. Constraints and assumptions

- Technology stack is fixed (see [SYSTEM_ARCHITECTURE.md](../architecture/SYSTEM_ARCHITECTURE.md)).
- Gabon's currency is XAF (CFA franc BEAC, no minor unit). All money handling uses integer minor units with an explicit currency and per-currency exponent.
- Mobile-money provider capabilities (hold/capture, refunds, webhooks, sandbox) are **unverified**; the payment design assumes only what is documented as "required capability" and must be validated with each provider before build. `[LEGAL]`
- No regulatory conclusion (payments licensing, tax treatment of service fees, data protection obligations, guest registration with authorities, short-term-rental rules) is assumed anywhere in these documents. See the `[LEGAL]` register in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md).

## 7. Success metrics (to be baselined)

Supply: approved properties, verification turnaround time. Demand: search-to-booking conversion, repeat booking rate. Quality: double-booking incidents (target 0), payment reconciliation exceptions per 1,000 transactions, notification delivery rate, support ticket resolution time. Revenue: service-fee revenue, subscription MRR, churn. Targets are set after discovery `[OD-14]`.
