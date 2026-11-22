# Business Model

## 1. Revenue streams

1. **Customer service fee** on each successful booking, charged to the customer on top of the accommodation price.
2. **Vendor subscriptions**, monthly or annual, in tiers Basic, Professional and Enterprise.

**There is no per-booking commission charged to hotels or hosts in the initial model.** The platform nevertheless supports a configurable vendor commission, **disabled by default** (ADR-35, approved 2026-11-21): this document's model is unchanged until a commission is explicitly activated for a market by an approved, audited configuration change.

### Commission and fee rules (supported, off by default)

- Customer service fee and vendor commission are both fee rules owned by the `pricing` module. Each rule has a kind (service fee or commission), a payer (customer or vendor), a shape (percentage, fixed, percentage with minimum and maximum), a market and currency, a validity range and a version.
- A rule can be enabled or disabled per market independently. No percentage is hardcoded.
- A booking stores an immutable snapshot of the rules applied when it was created; later rule changes never alter history.
- Open: the commission base, cancellation and refund treatment, and vendor-side invoicing `[OD-31]`; tax treatment `[LEGAL]` (L-4, L-19). Settlement of any commission follows ADR-20 (through the provider's split mechanism, never through Zella Stay holding funds).
- Rules can differ by market; no country is assumed to share another's commercial or regulatory rules ([MULTI_COUNTRY.md](../architecture/MULTI_COUNTRY.md)).

## 2. Customer service fee

- Configurable per country and currency, by an admin with the Finance Admin role. Supported shapes: percentage, fixed amount, percentage with min/max cap. Exact formula `[OD-2]`.
- Computed server-side at quote time, stored on the booking as an immutable snapshot (`service_fee_minor`, `service_fee_rule_id`, `service_fee_rule_version`).
- Earned (recognized) when the booking reaches `CONFIRMED` and payment has `SUCCEEDED`.
- Refund treatment on cancellation: fee refundability is part of the cancellation policy evaluation; default proposal is non-refundable after free-cancellation window, refundable inside it. `[OD-3]`
- Tax treatment of the service fee `[LEGAL]`.

## 3. Subscriptions

### 3.1 Plans
Plans are data (table `subscription_plan` + `plan_price` + `plan_entitlement`), managed in the Admin dashboard. Names Basic, Professional, Enterprise are the launch seed; admins can add, retire or reprice plans.

Configurable per plan:
- Price per billing period (monthly, annual) per currency.
- Limits (entitlements): max properties, max rooms/units, max staff accounts, max photos/videos per property, promotional campaigns per period, chat attachment storage, report export history.
- Feature flags: instant booking, request to book, promotional campaigns, advanced reports, multi-property dashboard, API/channel sync, priority support.
- Trial length and grace period.

Illustrative defaults are **not** specified in these docs; prices and limits are business inputs `[OD-4]`.

### 3.2 Subscription lifecycle

States: `TRIALING`, `ACTIVE`, `PAST_DUE`, `SUSPENDED`, `CANCELLED`, `EXPIRED`.

| Transition | Trigger |
|---|---|
| (new) → TRIALING | Organization approved and plan with trial selected |
| (new)/TRIALING → ACTIVE | First successful subscription payment |
| ACTIVE → PAST_DUE | Renewal payment fails |
| PAST_DUE → ACTIVE | Payment later succeeds within grace |
| PAST_DUE → SUSPENDED | Grace period elapsed |
| SUSPENDED → ACTIVE | Payment succeeds |
| ACTIVE → CANCELLED | Vendor cancels (effective at period end) |
| CANCELLED/SUSPENDED → EXPIRED | Period end / extended suspension |

### 3.3 Access restrictions by state

Principle: **a subscription problem never harms a guest with a confirmed booking.** Confirmed bookings, existing chats, settlements of earned funds and refunds continue in every state.

| Capability | TRIALING/ACTIVE | PAST_DUE | SUSPENDED | CANCELLED (after period end)/EXPIRED |
|---|---|---|---|---|
| Listings searchable | yes | yes | no | no |
| New bookings accepted | yes | yes (banner warning) | no | no |
| Manage existing bookings, chat, check-in | yes | yes | yes | yes (until all bookings complete) |
| Edit calendar/prices | yes | yes | read-only | read-only |
| Add properties/staff beyond limits | per plan | no | no | no |
| Reports, invoices, data export | yes | yes | yes | yes |

Plan downgrade over limit: existing resources above the new limit are kept but become read-only/unlisted by documented rule; no data is deleted. Enforcement is centralized in a single `EntitlementService` in the subscription module, consumed by other modules through a published interface (no scattered plan checks).

### 3.4 Billing mechanics
- Subscription charges use the same payment abstraction as bookings ([PAYMENT_ARCHITECTURE.md](../architecture/PAYMENT_ARCHITECTURE.md)). Mobile-money providers may not support recurring debit; if so, renewal is "invoice then pay" with reminders rather than automatic charge. `[OD-5]`
- Invoices are generated per period with sequential numbering per legal entity/country `[LEGAL]`.
- Proration on mid-period upgrade: proposal is immediate upgrade with prorated charge; downgrade at period end. `[OD-4]`

## 4. Funds flow (decided — ADR-20)

**Zella Stay does not custody customer funds.** Customer payments are collected, and vendor proceeds settled, by a **licensed marketplace payment provider** that performs collection, split and vendor settlement. Zella Stay never holds guest money in its own accounts unless and until qualified counsel confirms in writing that Zella Stay is legally authorized to do so (a change would need a new ADR).

Consequences:
- The platform's service fee and subscription charges reach Zella Stay through the provider's split/settlement mechanism (or a separate direct charge for subscriptions), not by Zella Stay moving guest money.
- Vendors are onboarded to the provider as sub-merchants/beneficiaries (provider-side KYC). Zella Stay stores only provider references and verification status, never full payout credentials.
- Refunds are executed by the provider against the original collection; Zella Stay requests them and tracks their state.
- Settlement timing to vendors (e.g., after check-in or after a hold period) is controlled through provider capabilities; what the provider can support is unverified (see [PAYMENT_PROVIDER_RESEARCH.md](../architecture/PAYMENT_PROVIDER_RESEARCH.md)).

**Mandatory validation before any production payment flow** `[LEGAL]` (register item L-1): (1) the chosen provider holds all licenses/authorizations required in Gabon for collecting and settling marketplace funds, (2) the arrangement (including split of service fee) is lawful for Zella Stay as a non-custodial platform, (3) tax and invoicing treatment, (4) provider agreement terms on refunds and chargebacks. Payment implementation may proceed against provider sandboxes behind the `PaymentProvider` abstraction, but **no live transaction is permitted before L-1 is closed**.

If no provider can satisfy the required capabilities (split/vendor settlement, refunds, webhooks, XAF, Airtel/Moov/cards), the product owner decides the next step; the engineering fallback is **not** to custody funds.

## 5. Promotions funding

- Platform-funded campaigns (loyalty points, promo codes, referral) reduce platform revenue (service fee) or are booked as marketing expense.
- Vendor-funded campaigns reduce the accommodation price; vendors may only create campaigns permitted by their plan and the platform rules (min/max discount, blackout dates, stacking rules). `[OD-13]`
- Stacking: at most one promo code per booking; loyalty redemption stacking is configurable.

## 6. Financial controls

- Double-entry accounting ledger in the payment module that **mirrors** provider-held and provider-settled amounts (customer payment, service fee, vendor settlement, refund, subscription revenue) for reporting and reconciliation; it is not a custody ledger.
- Daily reconciliation against provider settlement reports; unmatched items surface in the Admin dashboard.
- Four-eyes approval for manual refunds above a configured threshold and for settlement overrides.
- All pricing/fee/plan configuration changes are versioned and audit-logged.
