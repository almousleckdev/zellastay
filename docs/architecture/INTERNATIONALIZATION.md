# Internationalization and Localization

Launch locales: **French (`fr`, default)** and **English (`en`)**. Launch country: **Gabon (`GA`)**, currency **XAF**. Everything country-, language- or currency-dependent is data and strategy, never hardcoded.

## 1. Concepts (kept distinct)

| Concept | Meaning | Where it lives |
|---|---|---|
| Locale | Language + formatting (`fr`, `en`; later `fr-GA`, `pt`, `ar`…) | `cfg_locale`, user profile, request `Accept-Language` |
| Country | Market/regulatory jurisdiction | `cfg_country`; property and organization have one |
| Currency | ISO 4217 code with exponent | `cfg_currency` (XAF exponent 0, EUR 2, …) |
| Time zone | IANA zone (`Africa/Libreville`) | property, user; stored on property |
| Display vs transaction currency | Transactions occur in the property's pricing currency. Display conversion is **out of scope for launch** `[OD-16]` | |

## 2. Backend

- Money: `Money(amountMinor, CurrencyCode)`; formatting via currency exponent from config; rounding rule is explicit (half-even) and tested; percentages in basis points.
- Dates: UTC instants; property-local dates for stays; user-facing formatting by locale and appropriate zone at the edge.
- Message resolution for errors/validation: `MessageSource` bundles (`fr` default, `en`), keyed by stable codes (the API returns the code and localized message).
- **Authored content** (property descriptions, rules, amenity names, plan names, policy descriptions, notification templates): stored per locale in translation tables/jsonb. Vendors author in the languages they choose; fallback chain: requested locale → property's default locale → platform default (`fr`) → any available. Vendor UI indicates missing translations; machine translation is not applied silently `[OD-22]`.
- Locale for a request: `Accept-Language` > profile locale > country default > `fr`.
- Locale for asynchronous output (notifications, emails, invoices) is resolved at creation time and stored on the notification; guests store locale on the booking.
- Documents (invoices, vouchers) are generated per locale with country-specific legal fields supplied by configuration `[LEGAL]`.

## 3. Country configuration (per country)

Currency set, locales, time zones, tax rules (rate, base, inclusive/exclusive display), required vendor documents, address format and required address fields, phone number rules (E.164 validation by region), payment methods/providers, SMS sender rules, legal texts (terms, privacy) with versions, service-fee rules, cancellation-policy templates allowed, public holidays/seasons (optional aid for pricing), and feature flags. Adding a country = new config rows + adapters if provider differs + legal review + translations.

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

1. Legal/tax/payment review `[LEGAL]` (blocking).
2. `cfg_country` and dependent config rows; document requirements; tax rules; service-fee rules; cancellation templates.
3. Payment adapters (or reuse) and sandbox certification; SMS/email sender registration.
4. Seed attractions/amenities localization; address and geocoding validation.
5. Support operations and admin reviewer staffing for verification.
6. Observability dimensions (`country` tag) and dashboards.
7. Launch checklist signed off by product, engineering, finance and legal.
