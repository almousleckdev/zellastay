# Multi-Country Architecture

Zella Stay launches commercially in **Gabon** and is architecturally prepared for **Mali, Côte d'Ivoire and Senegal**, with more countries added progressively. Adding a country is configuration, a regulatory assessment and the required integrations, not a rewrite (ADR-38). No country list, currency, provider or business rule is hardcoded.

Status labels used below: **Approved** (explicit decision of 2026-11-21, recorded in [DECISIONS.md](../DECISIONS.md)), **Proposed** (recommended default adopted for implementation; confirm before dependent code ships), **Open** (tracked in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md)).

## 1. Concepts (never interchangeable)

| Concept | Meaning | Owner | Status |
|---|---|---|---|
| Country | ISO 3166-1 entity. Immutable facts (code, names). | `platformconfig` | Approved |
| Market | A country's commercial activation record: lifecycle state, effective dates, readiness. A country can exist in the registry without being a market that is open for business. | `platformconfig` | Approved |
| Jurisdiction | The legal regime that governs a topic. Usually national; some topics are supranational (for example a common business-law area or a monetary union). Mapped to countries by topic. | `platformconfig` | Proposed (minimal registry, no behavior depends on it yet) |
| Currency | ISO 4217 code with exponent. `XAF` (Gabon) and `XOF` (Mali, Côte d'Ivoire, Senegal) are different currencies even though both have exponent 0. They are never treated as equivalent and never converted implicitly. | `platformconfig` | Approved |
| Locale | Language plus formatting (`fr` default, `en`). | `platformconfig` | Existing |
| Time zone | IANA zone, stored on the property. Countries do not all share an offset. | `platformconfig` (allowed zones per country) | Approved |
| Customer location | Where the customer lives or pays from. Never decides property rules. | `identity` / `booking` snapshot | Approved |
| Property location | The property's country. Governs the booking. | `catalog` | Approved |

Facts about monetary unions, supranational law and time zones are general knowledge and must be confirmed by counsel before they appear in legal texts `[LEGAL]`.

## 2. Governing-country matrix (Proposed)

Every rule names exactly one governing country so two modules can never disagree.

| Concern | Governed by | Notes |
|---|---|---|
| Accommodation tax and tourism tax | Property's country | Approved |
| Booking currency | Property's pricing currency | Approved; no automatic conversion |
| Pricing rules, customer service fee, vendor commission | Property's market and currency | Fee and commission are separate rules, see §5 |
| Cancellation policy and templates | Property's country | Approved |
| Property regulations and listing requirements | Property's country | Approved |
| Vendor verification documents (organization) | Organization's legal-entity country | Property licences use the property's country |
| Subscription price, currency and tax | Organization's country | Subscription tax rules are owned by `subscription` |
| Payment methods offered | Property's market and currency; cards are the cross-border exception | See §6 |
| Phone number validation, SMS sender rules | The number's own region | Not the user's country |
| Interface language | User preference, then market default, then `fr` | Existing |
| Legal texts shown | The user's market | Existing |

## 3. Configuration ownership (Approved)

`platformconfig` owns **registries and configuration infrastructure**. Each business module owns the rules it interprets and the calculations that use them.

| Module | Owns |
|---|---|
| `platformconfig` | Country registry, market lifecycle and activation, currency, locale, time-zone and jurisdiction registries, global platform parameters, feature flags |
| `pricing` | Taxes on stays, customer service fees, vendor commissions, pricing rules |
| `payment` | Payment methods, provider configuration, payment routing |
| `onboarding` | Vendor verification requirements, country document requirements |
| `booking` | Cancellation policies and templates, and the property-to-policy selection |
| `subscription` | Subscription plans and, as a consequence of the module graph, the tax rules applied to subscription invoices (Proposed, ADR-36) |

Rules:
- No generic configuration system storing every business rule as JSON. Rule families are typed tables with constraints. `cfg_parameter` (jsonb) is only for scalar thresholds and timers.
- Every rule table is versioned and effective-dated. Bookings and invoices snapshot the version they used.
- Changes follow draft, approve, publish. Financial rule changes need a second approver and are audited.
- Consumers read an immutable configuration snapshot through the owning module's `api`. A change publishes an event so caches refresh.
- Behavior that data cannot express (for example an unusual tax computation) is a named strategy registered in code and selected by configuration. Code never branches on a country code.

Consequences of the split:
- `cat_property.cancellation_policy_code` moves into `booking` (`book_property_policy`) because `catalog` cannot depend on `booking` without a cycle (Proposed).
- `subscription` cannot depend on `pricing` (it sits below it in the graph), so stay tax and subscription tax are separate rule families. Arithmetic is shared through `Money` and its rounding rule, so no business logic is duplicated (Proposed).

## 4. Market lifecycle and readiness (Approved launch strategy, Proposed mechanics)

States: `DRAFT` → `CONFIGURING` → `READY_FOR_REVIEW` → `ACTIVE` ⇄ `SUSPENDED` → `RETIRED`.

- Activation is data, never a deployment. It requires a second approver (four-eyes) and is audited.
- Readiness uses a published SPI: `platformconfig.api` defines `MarketReadinessCheck`. Owning modules implement it and register beans, so `platformconfig` collects them at runtime with no upward compile dependency (the same pattern as `AudienceRoutes`).
- Checks per module: `pricing` (tax and fee rules present), `payment` (at least one certified provider method), `onboarding` (document requirements), `booking` (cancellation templates), `notification` (French and English templates, sender registration), plus a recorded legal clearance per market (L-1 and the other per-country items).
- Other modules ask `platformconfig.api` whether a country is bookable. Catalog publication and booking creation fail closed for markets that are not `ACTIVE`.
- An automated test proves a market cannot become `ACTIVE` while any check fails.

## 5. Fees: service fee and vendor commission (Approved)

- The customer service fee exists today ([BUSINESS_MODEL.md](../product/BUSINESS_MODEL.md)). **Vendor commission is supported but disabled by default**; the business model is unchanged until a commission is explicitly activated for a market.
- Both are `price_fee_rule` rows: `kind` in {`SERVICE_FEE`, `COMMISSION`}, `payer` in {`CUSTOMER`, `VENDOR`}, shape (percentage, fixed, percentage with minimum and maximum), market, currency, validity range, version, status.
- A rule can be enabled or disabled independently per market. No percentage appears in code.
- Each booking stores an immutable snapshot of the rules applied (`service_fee_*`, `commission_*`, rule ids and versions). History is never recomputed from current rules.
- Open: the commission base, cancellation treatment and vendor-side invoicing (OD-31).

## 6. Cross-border bookings and payment routing (Approved)

Cross-border bookings are allowed. Example: a customer in Gabon books a hotel in Mali.

- The property's country governs taxes, booking currency, pricing, cancellation policy and regulations. The booking snapshots `property_country` and, separately, `customer_country` (nullable).
- International cards are supported for cross-border bookings. Mobile money availability is determined by the property's market and the supported provider integrations. A method available in one country is never assumed to work in another.
- Routing key: (property market, booking currency, method, payer scope). A provider integration declares each method as domestic-only or as supporting a stated payer country.
- The customer is charged in the property's currency. There is **no automatic conversion**; any conversion by a card issuer is disclosed to the customer `[LEGAL]` (L-10). Refunds use the original currency and original method.
- Open: how the payer country is determined, and who bears cross-border refund and exchange losses (OD-34); tax on the service fee by customer location (L-19).

## 7. Country-scoped administration (Proposed)

Admin role grants carry a scope: all countries, or a list of countries. Permission checks take the governing country when the action is country-specific. Existing grants migrate to all countries, so current behavior is unchanged. Super Admin remains global. Audit records carry a nullable `country`. Detail in ADR-40.

## 8. Enforcement

- A source test bans country, currency and provider literals in `backend/src/main` outside migrations and test code (with an explicit allow-list).
- A market-readiness test (see §4).
- Configuration completeness checklist: [INTERNATIONALIZATION.md](INTERNATIONALIZATION.md) §8.
- Reference data (countries, currencies, locales, time zones) is seeded only through migrations marked as configuration, never as test data.

## 9. Open items

Commission mechanics (OD-31), organizations operating outside their legal-entity country (OD-32), Zella Stay legal entities per country (OD-33), payer country and cross-border refund losses (OD-34), per-country legal register (L-1, L-5 to L-7, L-12, L-19), and payment provider research for Mali, Côte d'Ivoire and Senegal (OD-18). Which markets are seeded as `DRAFT` is open (OD-35, see [OPEN_DECISIONS.md](../OPEN_DECISIONS.md)).
