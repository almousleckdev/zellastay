# Internationalization and Localization

Launch locales: **French (`fr`, default)** and **English (`en`)**. First commercial market: **Gabon (`GA`)**; Mali, Côte d'Ivoire and Senegal are prepared architecturally and further countries follow (ADR-38). Everything country-, language- or currency-dependent is data and strategy, never hardcoded. The full model, governing-country matrix and market lifecycle are in [MULTI_COUNTRY.md](MULTI_COUNTRY.md).

## 1. Concepts (kept distinct)

| Concept | Meaning | Where it lives |
|---|---|---|
| Locale | Language + formatting (`fr`, `en`; later `fr-GA`, `pt`, `ar`…) | `cfg_locale`, user profile, request `Accept-Language` |
| Country | ISO country, immutable facts | `cfg_country` |
| Market | A country's commercial activation record and lifecycle | `cfg_market` |
| Jurisdiction | The legal regime governing a topic (national or supranational) | `cfg_jurisdiction`, `cfg_country_jurisdiction` |
| Currency | ISO 4217 code with exponent; `XAF` and `XOF` are different currencies, never interchangeable | `cfg_currency` (exponent 0 for both, 2 for EUR) |
| Time zone | IANA zone (`Africa/Libreville`); countries do not share one offset | property; allowed zones in `cfg_country_time_zone` |
| Customer location | Where the customer is; never decides property rules | profile, booking snapshot (`customer_country`, nullable) |
| Property location | The property's country; governs taxes, currency, cancellation and regulations (ADR-37) | `cat_property.country`, booking snapshot (`property_country`) |
| Display vs transaction currency | Transactions occur in the property's pricing currency with no automatic conversion. Display conversion is **out of scope** `[OD-16]` | |

## 2. Backend

- Money: `Money(amountMinor, CurrencyCode)`; formatting via currency exponent from config; rounding rule is explicit (half-even) and tested; percentages in basis points.
- Dates: UTC instants; property-local dates for stays; user-facing formatting by locale and appropriate zone at the edge.
- Message resolution for errors/validation: `MessageSource` bundles (`fr` default, `en`), keyed by stable codes (the API returns the code and localized message).
- **Authored content** (property descriptions, rules, amenity names, plan names, policy descriptions, notification templates): stored per locale in translation tables/jsonb. Vendors author in the languages they choose; fallback chain: requested locale → property's default locale → platform default (`fr`) → any available. Vendor UI indicates missing translations; machine translation is not applied silently `[OD-22]`.
- Locale for a request: `Accept-Language` > profile locale > country default > `fr`.
- Locale for asynchronous output (notifications, emails, invoices) is resolved at creation time and stored on the notification; guests store locale on the booking.
- Documents (invoices, vouchers) are generated per locale with country-specific legal fields supplied by configuration `[LEGAL]`.

## 3. Country configuration (per country)

Currency set, locales, time zones, tax rules (rate, base, inclusive/exclusive display), required vendor documents, address format and required address fields, phone number rules (E.164 validation by region), payment methods/providers, SMS sender rules, legal texts (terms, privacy) with versions, service-fee and commission rules, cancellation-policy templates allowed, public holidays/seasons (optional aid for pricing), and feature flags. Adding a country = new config rows + adapters if provider differs + legal review + translations.

**Ownership (ADR-36):** the registries (country, market, currency, locale, time zone, jurisdiction), global parameters and feature flags live in `platformconfig`. Tax, service-fee and commission rules live in `pricing`; payment methods and routing in `payment`; document requirements in `onboarding`; cancellation templates in `booking`. See [MULTI_COUNTRY.md](MULTI_COUNTRY.md) §3.

Tax display: prices shown to customers include or exclude taxes per country rule; the breakdown is always available. Rules `[LEGAL]`.

## 4. Web apps (Next.js)

- Locale-aware routing (`/fr/...` default, `/en/...`), implemented with a maintained i18n library (e.g., `next-intl`) `[ADR-8]`; `hreflang` and localized metadata for SEO on the public site.
- Message catalogs in JSON per locale, keys namespaced by feature; CI checks key parity and unused keys; no hardcoded user-visible strings (lint rule).
- Formatting with `Intl` APIs using the active locale and the currency from the API payload. Plural and gender rules via ICU message format.
- Right-to-left readiness: logical CSS properties (`margin-inline-start`) and `dir` support from day one so future RTL locales don't require a rewrite.
- Slugs and URLs are locale-aware where public.

## 5. Mobile (Flutter)

- `flutter_localizations` + ARB files with generated code; locales `fr` (default) and `en`; device locale respected with in-app override persisted and synced to profile.
- `intl` for number/date/currency formatting; never string-concatenate translated fragments.
- Push notification text comes localized from the server according to the stored profile/device locale.

## 6. Content and design requirements

- UI must tolerate text expansion (French is typically longer than English): no fixed-width labels, truncation rules defined in [DESIGN_SYSTEM.md](../frontend/DESIGN_SYSTEM.md).
- Icons and imagery avoid locale-specific text; units (m², km) configurable per country.
- Names/addresses: do not assume first/last name structure everywhere; support a single "full name" display and structured components where needed; phone numbers always stored as E.164.

## 7. Adding a language (checklist)

1. Add `cfg_locale` row; enable per country.
2. Add backend message bundle, web catalogs, ARB file.
3. Translate notification templates and system chat messages; verify the template completeness test.
4. Review legal texts with counsel.
5. Review layout (expansion, RTL if applicable), fonts/glyph coverage.
6. Add locale to SEO config and sitemap.
7. QA pass through critical flows (search → book → pay → confirm).

## 8. Adding a country (checklist)

1. Legal/tax/payment review `[LEGAL]` (blocking), including L-1 closed for that country.
2. `cfg_country`, `cfg_market` and dependent registry rows in `platformconfig`; then each owning module's rows: document requirements (`onboarding`), tax, service-fee and commission rules (`pricing`), payment methods (`payment`), cancellation templates (`booking`), subscription tax (`subscription`).
2a. Market readiness: every module's `MarketReadinessCheck` passes and a second approver activates the market (ADR-38). Activation is data, not a deployment.
3. Payment adapters (or reuse) and sandbox certification; SMS/email sender registration.
4. Seed attractions/amenities localization; address and geocoding validation.
5. Support operations and admin reviewer staffing for verification.
6. Observability dimensions (`country` tag) and dashboards.
7. Launch checklist signed off by product, engineering, finance and legal.
