# Open Decisions, Legal Validation Register and Risks

Nothing here may be silently decided by implementers. Resolve by updating the relevant documents and moving the item to [DECISIONS.md](DECISIONS.md). "Blocks" names the earliest phase that cannot start until resolved ([ROADMAP.md](product/ROADMAP.md)).

## 0. Resolved decisions

| ID | Decision | Recorded as |
|---|---|---|
| OD-1 | Funds flow: a licensed marketplace payment provider handles customer collection and vendor settlement; Zella Stay does not custody customer funds without confirmed legal authorization. Gabon legal and provider validation (L-1) is **mandatory** before any live transaction. | ADR-20 |
| OD-6 | Request to Book is approve-then-pay. A request holds no inventory; on approval inventory is reserved temporarily while awaiting payment; payment deadline is configurable; expired reservations release inventory safely. | ADR-13 |
| OD-27 | Price validity between request and approval: the price snapshot taken at request time is honored at approval and payment. If the price has changed by the time of approval, the new price **requires explicit customer acceptance** before payment can proceed; the customer is never charged a changed price silently. | ADR-23 |
| OD-28 | Vendors cannot accept live bookings until provider-side settlement beneficiary verification succeeds. For an organization without a verified beneficiary, instant booking creation is rejected and request approval is rejected; verification state is checked server-side at booking creation and at vendor approval. | ADR-24 |

## 1. Open business and product decisions

| ID | Question | Working assumption in docs | Blocks |
|---|---|---|---|
| OD-2 | Exact customer service-fee formula (percentage, fixed, caps, tax on fee) per country | Configurable rule shapes | Phase 2 |
| OD-3 | Cancellation policy templates offered to vendors; refundability of service fee and taxes | Platform-defined tiered templates; fee refundable inside free-cancellation window | Phase 2 |
| OD-4 | Plan prices, limits, trial length, grace period, proration rules | Data-driven; no defaults assumed | Phase 2 |
| OD-5 | Recurring subscription billing viability with mobile money (automatic debit vs invoice-and-pay) | Invoice-and-pay fallback supported | Phase 2 |
| OD-7 | Penalties/ranking impact for vendor-initiated cancellations | Full refund to guest; penalty rules TBD | Phase 2 |
| OD-8 | Location privacy for private homes (approximate vs exact until confirmed) | Approximate until `CONFIRMED` | Phase 1 (catalog) |
| OD-9 | Identity model: one person across customer/vendor/admin contexts; membership in several organizations; custom vendor roles | Distinct account kinds per audience; fixed five vendor roles; multiple memberships allowed | Phase 1 |
| OD-10 | ~~Build identity in-house vs adopt external IdP~~ **Decided: in-house** (ADR-30) | In-house `identity` module behind API | Closed |
| OD-11 | Pricing rule scope at launch (weekday, length-of-stay, per-guest supplements, closed-to-arrival, min/max stay, extra-bed fees) | Season, weekday, date override, min/max stay, per-guest supplements | Phase 2 |
| OD-12 | iCal / channel-manager synchronization | Post-launch | Future |
| OD-13 | Scope of vendor-created campaigns and platform rules (max discount, stacking, funding) | Within plan entitlement; one code per booking | Phase 4 |
| OD-14 | Quantified SLOs, p95 latency targets, availability, RPO/RTO, mutation-score targets, performance budgets | None set | Phase 2 exit |
| OD-15 | Cloud provider, region, hosting model for web apps, admin network restriction; data residency | Cloud-agnostic containers | Phase 0 |
| OD-16 | Cross-border bookings, display-currency conversion | Out of scope at launch | Phase 5 |
| OD-17 | Hash-chained / WORM audit log. Slice A ships append-only storage without hash chaining (ADR-32); revisit before public launch | Append-only privileges; hash chain optional | Before launch |
| OD-18 | **Payment provider selection** (card PSP, Airtel/Moov route, marketplace collection + settlement provider). Not selected. Research and C1–C15 capability checklist in [PAYMENT_PROVIDER_RESEARCH.md](architecture/PAYMENT_PROVIDER_RESEARCH.md); main gaps: split/vendor settlement undocumented, Moov Gabon unconfirmed, refunds undocumented for some candidates | None selected; provider-neutral core built against a simulator | Phase 2 (live adapters) |
| OD-19 | Room assignment timing (at booking vs later); selling entire property and parts simultaneously | Type-level booking, assignment later; composite blocking supported | Phase 2 |
| OD-20 | Email/SMS/push providers per country | Unselected | Phase 1 (notification) |
| OD-21 | Chat windows after completion/cancellation, presence exposure, link/off-platform policy, retention durations | 14 days / 7 days / no presence / pattern rules | Phase 3 |
| OD-22 | Machine translation of vendor content | Not applied silently | Phase 2 |
| OD-23 | Mobile platform priority and minimum OS versions | Android and iOS, Android first | Phase 0 |
| OD-24 | Certificate pinning in mobile app | Not at launch | Phase 2 |
| OD-25 | Maps / geocoding provider | Abstraction required | Phase 1 |
| OD-6a | Booking timer values (vendor response window, payment windows per method, in-flight payment grace, auto-complete grace). **Decided:** the values are product-configurable platform parameters (per country, overridable per payment method), never code constants. **Pending:** the final values, to be supplied by product | None in code; startup/feature enablement fails if a required timer is unset | Phase 2 |
| OD-29 | **Cloudinary verification spike (Phase 1, before any upload feature ships):** (a) authenticated delivery and signed expiring URLs for PDFs/scans (documentation covers images and videos only; raw files unconfirmed); (b) malware scanning for documents (own scanner in the server-proxied path vs the provider's paid add-on, pricing unknown); (c) restricting arbitrary unsigned transformations; (d) backup/purge behavior on delete; (e) plan limits (asset sizes, API rate limits, add-ons) against expected volumes; (f) whether re-typing an asset from `authenticated` to `upload` is possible when a property is published; (g) EU/other data location (Enterprise only per documentation) | Design assumes all positive; fallbacks listed in MEDIA_ARCHITECTURE §4 | Phase 1 |
| OD-30 | Media limits per kind: maximum file sizes, image dimensions, video duration/size, maximum photos/videos per property per plan tier (ties to subscription entitlements), allowed formats review | Initial formats in MEDIA_ARCHITECTURE §2; no numeric limits assumed | Phase 1 |
| OD-26 | Brand identity (palette, typography, logo), dark mode scope | Placeholder tokens | Phase 1 |

## 2. Legal / regulatory / provider validation register (`[LEGAL]`)

No legal or regulatory conclusion is assumed in any document. Each item needs qualified local counsel or the relevant provider/regulator confirmation before the dependent work ships.

| # | Topic | Affects |
|---|---|---|
| L-1 | **MANDATORY — blocks any live transaction.** Confirm in writing that the chosen provider holds every authorization required in Gabon to collect guest funds and settle to vendors, that the split/fee arrangement is lawful for Zella Stay as a non-custodial platform, and that Zella Stay needs no license of its own | ADR-20, payment, settlements |
| L-2 | Mobile-money provider terms, capabilities (refunds, webhooks, recurring), settlement and fees | Payment adapters, OD-5, OD-18 |
| L-3 | Card acceptance requirements, PCI scope, 3-D Secure requirements, PSP onboarding | Payment, security |
| L-4 | Tax treatment: accommodation taxes, tourism taxes, VAT on customer service fee and on subscriptions; invoicing format and numbering; remittance responsibility | Pricing, invoices, reporting |
| L-5 | Personal-data protection law(s) and authority: lawful basis, consent, DPO, registration, rights handling, breach notification timelines | Identity, chat, notifications, analytics |
| L-6 | Cross-border transfer and data-residency requirements | Hosting (OD-15), subprocessors |
| L-7 | Retention periods: financial, audit, identity documents, chat, notification content | Retention jobs |
| L-8 | Collection and storage of identity documents (hosts and guests); guest-registration duties toward authorities | Onboarding, booking |
| L-9 | Short-term rental / hospitality licensing and classification rules; platform liability; required listing disclosures | Onboarding requirements, terms |
| L-10 | Consumer protection: price display, cancellation/refund disclosures, review authenticity rules | Booking UX, reviews |
| L-11 | Electronic communications consent rules (SMS/email marketing, sender registration) | Notifications |
| L-12 | Required vendor document list per country (business registration, tax id, etc.) | `cfg_document_requirement` |
| L-13 | Terms of service, privacy policy, vendor agreement content | Legal texts |
| L-14 | Minors' data / age requirements | Identity |
| L-15 | Loyalty/promotion regulation (points expiry, gift/voucher treatment, referral incentives) | Loyalty, promotion |
| L-17 | Applicability to this product of any Gabonese requirement for payment operators to connect to a national digital aggregator (mentioned in a gaming context in one source) | Payment |
| L-18 | Use of Cloudinary and its sub-processors for identity and business documents: DPA, storage region, retention after deletion, backups; lawful basis for third-party hosting of identity documents | Media, onboarding |
| L-16 | Disputes/chargebacks handling obligations | Payment, support |

## 3. Technical risks

| ID | Risk | Likelihood / impact | Mitigation |
|---|---|---|---|
| R-1 | Mobile-money provider limitations (no refund API, unreliable webhooks, no sandbox) | High / High | Early provider spike; polling + reconciliation; manual refund path; capability matrix before design freeze |
| R-2 | **No provider found that publicly documents marketplace split + vendor settlement on Airtel/Moov Gabon**, or legal validation (L-1) fails; ADR-20 forbids custody as a fallback | High / Critical | Provider due diligence first (PAYMENT_PROVIDER_RESEARCH §4); escalate to product owner if none qualifies; build provider-neutral core against simulator meanwhile |
| R-3 | Inventory race conditions or deadlocks under load | Medium / Critical | DB-level constraints, ordered locking, concurrency suite in CI, invariant monitors |
| R-4 | Connectivity/latency in Gabon degrades UX | High / Medium | Small payloads, caching, resilient retries with idempotency, regional hosting consideration (OD-15), CDN |
| R-5 | Duplicate/lost payment states from lost webhooks | Medium / High | Polling, status re-query, reconciliation, anomaly refunds |
| R-6 | Modular monolith boundary erosion | Medium / High | Modulith verification + ArchUnit in CI, ownership, review checklist |
| R-7 | Scope breadth (4 apps + 21 modules) exceeds capacity | High / High | Phased roadmap; vertical slices; defer loyalty/promotions/chat to later phases; cut by phase, not by quality |
| R-8 | RLS and JPA interplay complexity/performance | Medium / Medium | Phase 0 spike; fallback to application-level scoping + tests |
| R-9 | STOMP simple broker limits at scale | Low / Medium | Redis bridge; load test; broker relay ADR if needed |
| R-10 | Fraud: fake listings, promo abuse, SMS pumping, chat abuse | Medium / High | Mandatory verification, rate limits, country allow-lists for OTP, monitoring |
| R-11 | Template/content localization gaps | Medium / Low | CI completeness checks, fallback chain |
| R-12 | Vendor adoption/low supply at launch | Medium / High | Business risk; onboarding UX; support operations (outside engineering docs) |
| R-13 | Data protection/retention non-compliance | Medium / High | L-5 to L-7 resolved before launch; retention jobs; privacy-by-design |
| R-14 | Time-zone and date-boundary bugs in stays and cancellation tiers | Medium / Medium | Property-local `date` semantics, injected clock, boundary tests |
| R-16 | Cloudinary cannot meet private-document requirements (raw/PDF authenticated delivery, malware scanning, data location) or incurs unexpected cost from uncontrolled transformations | Medium / High | OD-29 spike before building document upload; named variants only; documented fallback options in MEDIA_ARCHITECTURE §4; a new ADR if documents need a different store |
| R-17 | The bundled password denylist (`identity/password-denylist.txt`) is a starter list, so weak passwords outside it are accepted | High / Medium | Replace with a vetted large corpus before public launch; consider an online k-anonymity breach check behind a port (security review first) |
| R-18 | `X-Forwarded-For` is trusted for rate limiting and audit IP addresses; a backend reachable directly could be spoofed | Medium / High | Deploy only behind a proxy that overwrites the header; network policy blocks direct access ([DEVOPS.md](engineering/DEVOPS.md) section 12) |
| R-15 | Over-engineering (premature Kafka, extra abstractions) | Medium / Medium | ADR triggers; "no unnecessary abstractions" review gate |
