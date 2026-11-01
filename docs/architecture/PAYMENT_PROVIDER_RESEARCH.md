# Payment Provider Research (OD-18)

Research date: 2026-11-01. **No provider is selected.** This document records what public sources say about collecting and settling Airtel Money, Moov Money and card payments in Gabon (XAF), so a shortlist and due-diligence questionnaire can be prepared. Sources are public web pages and vendor documentation read once; several are third-party or model-summarized. **Nothing below is a verified commitment from any provider**, and nothing is legal advice. Required context: [ADR-20](../DECISIONS.md) (licensed marketplace provider collects and settles; Zella Stay does not custody funds) and legal item L-1/L-2.

Confidence labels: **Doc** = stated in the provider's own developer documentation; **Claim** = marketing or coverage page; **3rd** = third-party blog/SDK/aggregator article; **None** = not found.

## 1. Required capabilities (acceptance checklist for any candidate)

| # | Capability | Why |
|---|---|---|
| C1 | Licensed/authorized to collect and settle marketplace funds in Gabon (written confirmation) | ADR-20, L-1 |
| C2 | Airtel Money Gabon collections in XAF | Launch method |
| C3 | Moov Money Gabon collections in XAF | Launch method |
| C4 | Visa/Mastercard in XAF with 3-D Secure, tokenized/hosted entry | Launch method, PCI scope |
| C5 | Split payments / sub-merchant (vendor) settlement with platform fee retained | ADR-20 |
| C6 | Configurable settlement timing (e.g., release after check-in) | Vendor protection |
| C7 | Refund API: full and partial, per-method behavior, timelines, idempotency | Refund workflow |
| C8 | Webhooks with verifiable signatures, retries, status query API | Reconciliation, idempotency |
| C9 | Idempotent initiation (client-supplied unique id) | Payment architecture §5 |
| C10 | Settlement/transaction reports (API or file) for reconciliation | Reconciliation |
| C11 | Recurring or merchant-initiated billing (else invoice-and-pay fallback) | OD-5 |
| C12 | Sandbox with realistic Gabon test data | Contract tests |
| C13 | Chargeback/dispute process and fees | L-16 |
| C14 | Fees, minimums, limits (per-transaction, daily) | Business model |
| C15 | Vendor KYC onboarding flow and SLAs | Vendor onboarding |

## 2. Findings by rail

### 2.1 Airtel Money (Gabon)
- Direct API: official developer portal exists (developers.airtel.africa per third-party SDK READMEs). The Gabon-specific merchant documentation, onboarding terms, refund and callback specifics were **not found** in official form (**None** for C7/C8/C10/C11 at Airtel direct). Third-party wrappers describe `merchant/v2/payments` (collection) and `payouts` (disbursement) endpoints, callbacks with a signature header, and refunds referenced by Airtel transaction id (**3rd**, not Gabon-specific).
- One third-party page claims refunds are treated as a separate forward payment, not a reversal, with a reversal path by merchant request (**3rd**; unconfirmed).
- Market position: reported as the majority mobile-money operator in Gabon (figures from unsourced vendor blogs; ranges 65–90%+) — treat as indicative only.
- Aggregators that list Airtel Gabon: pawaPay (**Doc**: provider `AIRTEL_GAB`, XAF, sandbox test MSISDNs), Moneroo (**Claim**), Rafiki (**3rd**: min 500 XAF, max 500,000 XAF), E-Billing / SingPay / PViT (**3rd**, via an open-source wrapper).

### 2.2 Moov Money (Gabon)
- No official Moov Africa Gabon merchant API documentation found (**None**). Evidence for direct integration comes only from Benin (contract with operator required) (**3rd**) — Gabon unconfirmed.
- pawaPay documentation lists Moov Money for Benin, Burkina Faso, Côte d'Ivoire and Mali but **not Gabon** (**Doc**) — i.e., pawaPay does not appear to cover Moov Gabon today.
- Moneroo's coverage page lists "Moov Money Gabon" (**Claim**), but its API method list shows no Gabon mobile-money code (only Gabon cards and crypto) (**Doc**) — coverage claim and API reality disagree; needs direct confirmation.
- E-Billing / SingPay / PViT list Moov via a third-party wrapper (**3rd**). Another vendor blog says Moov Money has "partial API availability" (**3rd**, unsourced).
- **Implication:** Moov Gabon coverage is the least certain launch rail; a provider that cannot confirm it in writing does not satisfy C3.

### 2.3 Cards (Visa / Mastercard)
- Moneroo lists Visa/Mastercard XAF for Gabon among six CEMAC countries (**Claim** and listed as `card_xaf` in docs). It states it is **not a PSP or acquirer** and only facilitates calls to underlying gateways (**Doc**, homepage footer), so the acquiring/licensing party is a different entity that must be identified.
- General guides name Worldline and InTouch (regional CEMAC aggregator for cards, mobile money, transfers), PayDunya, Orange Money as active in Gabon, and list Stripe, Adyen and others in generic go-to-market text (**3rd**, model-summarized page). Whether Stripe/Adyen can onboard a Gabon-registered marketplace or settle in XAF is **not confirmed** (**None**).
- Generic guidance: card fees 2.5–3.5%, chargeback fees XAF 10,000–30,000, 3-D Secure strongly advised (**3rd**; pricing unverified).

### 2.4 Webhooks, signatures, idempotency (as documented)

| Provider | Webhooks / signatures | Idempotency | Status query |
|---|---|---|---|
| pawaPay | Callbacks to configured URLs; optional RFC 9421 HTTP signatures (Content-Digest, Signature, public-key endpoint), enabled in dashboard (**Doc**) | Payment id is the idempotency key; reuse returns `DUPLICATE_IGNORED` (**Doc**) | Yes (check-status; polling when no callback URL) (**Doc**) |
| Moneroo | `X-Moneroo-Signature` = HMAC-SHA256 of payload with webhook secret; events payment/payout `*.success/failed/...`; 3 s timeout; 3 retries at 10-minute intervals; payloads partial, re-query API (**Doc**). Doc examples disagree on hashing raw body vs re-serialized JSON — must be clarified | Not documented in pages read | Yes (verify/retrieve) (**Doc**) |
| Airtel direct | Signature header described by wrappers; exact scheme unverified (**3rd**) | Unknown | Unknown |
| Moov direct | Unknown | Unknown | Unknown |

### 2.5 Refunds

| Provider | Refund support |
|---|---|
| pawaPay | `POST /v2/refunds` by `depositId`, partial supported, total refunds ≤ deposit else rejected, one refund in progress at a time (`REFUND_IN_PROGRESS`) (**Doc**). Per-operator behavior in Gabon and timelines not read. |
| Moneroo | No refund documentation found (**None**). |
| Airtel / Moov direct | Not confirmed (**3rd** hints at separate-payment semantics). |
| Card acquirers | Not researched (candidate-specific). |

Design consequence already in [PAYMENT_ARCHITECTURE.md](PAYMENT_ARCHITECTURE.md): refunds are asynchronous, idempotent, serialized per payment where required, with a manual path when a rail cannot refund.

### 2.6 Settlement, split payments, marketplace model
- **Split/sub-merchant/marketplace payments: not documented** by pawaPay or Moneroo in the pages read (**None** for C5). Search results for XAF marketplace split providers surfaced no confirmed provider; one XAF-focused vendor claims funds never pass through it and licensed providers process transactions, without naming them or confirming split (**Claim**). dLocal advertises a platforms/split module in Africa but XAF/CEMAC coverage is unconfirmed (**Claim**).
- pawaPay: settlement in local or global currency, scheduled or on-request; wallets, statements and settlement endpoints exist; "aggregator" model where pawaPay collects on behalf of the merchant and holds the regulatory permissions (**Doc/Claim**). A Gabon-specific license statement was not found; only a Kenyan PSP authorization is stated.
- Generic Gabon PSP guidance: settlement 24–72 h (local) vs 3–7 business days (FAQ) — the same page contradicts itself (**3rd**); minimum payout and payout fees quoted as ranges.
- Regional rails: GIMACPAY connects banks and mobile-money operators across CEMAC (**3rd**).

**Key risk (R-2 update):** ADR-20 requires collection + vendor settlement by a licensed provider. Public documentation found does **not** demonstrate any provider offering marketplace split with retained platform fee on Airtel/Moov Gabon. This must be resolved by direct provider conversations before Phase 2 design freeze.

### 2.7 Recurring / subscription billing
Not documented by pawaPay or Moneroo in pages read; Airtel/Moov direct unconfirmed (**None** for C11). Plan for invoice-and-pay renewal (OD-5) until a provider confirms merchant-initiated debit.

### 2.8 Sandbox
- pawaPay sandbox (`api.sandbox.pawapay.io/v2`) with Gabon Airtel test MSISDNs (**Doc**).
- Moneroo sandbox with a default test gateway (USD, non-Gabon test numbers) and sandbox keys; Gabon-specific sandbox not shown (**Doc**).
- Airtel/Moov direct: unknown.

### 2.9 Regulatory context to raise with counsel (not conclusions)
- BEAC/CEMAC framework (currency controls, AML/KYC) and COBAC supervision are the likely relevant bodies; sources disagree or omit specifics (one page named BCEAO, a West African body, likely erroneously).
- Vendor KYC documents may need French or certified translation and legal formalities for foreign documents (**3rd**).
- A source mentions a Gabonese requirement for operators to connect to a national digital aggregator operated by e-Tech SAS; the source context was gaming/betting, so applicability to accommodation payments is **unknown** and must be checked.
- VAT 18% appears in a generic guide (**3rd**); tax treatment is for counsel (L-4).

## 3. Candidate longlist (for due diligence only — not a selection)

| Candidate | What public sources suggest | Open gaps |
|---|---|---|
| pawaPay | Gabon Airtel XAF (Doc), refunds (Doc), idempotent, signed callbacks, sandbox, settlement options | Moov Gabon absent; cards not offered (mobile-money focus); split/marketplace and recurring undocumented; Gabon license statement |
| Moneroo | Claims Airtel, Moov and cards in Gabon; HMAC webhooks; sandbox | Not a PSP; mobile-money Gabon absent from API method list; refunds, split, settlement, recurring undocumented; underlying licensed parties unknown |
| InTouch (CEMAC aggregator) | Cards + mobile money + bank transfers regionally (3rd) | No developer docs read; all capabilities unverified |
| E-Billing / SingPay / PViT | Local aggregators supporting Airtel and Moov (3rd) | Docs, licensing, split, refunds unverified |
| Worldline (cards) | Named as international card PSP present in Gabon (3rd) | Marketplace/sub-merchant model, XAF settlement, onboarding unverified |
| PayDunya, Rafiki, Genuka Pay, dLocal | Mentioned in sources | Gabon marketplace capability unverified |
| Direct Airtel / Moov Gabon | Official routes exist or may exist | Terms, APIs, marketplace rules unverified |

## 4. Next steps (owners: product + finance + counsel; engineering supports)

1. Send the C1–C15 questionnaire to the longlist and obtain written answers, sandbox access and indicative pricing.
2. Ask each candidate explicitly: "Which licensed entity collects funds and settles to our vendors in Gabon, and under which authorization?" (C1/L-1).
3. Ask Airtel Gabon and Moov Africa Gabon directly about merchant APIs, refunds, callbacks and marketplace/aggregator rules.
4. Counsel validates the shortlisted provider arrangement (L-1, L-2, L-3, L-4, L-16).
5. Only then record the selection in `DECISIONS.md` and implement adapters behind `PaymentProvider`. Until then engineering may build the provider-neutral core (state machine, ledger, idempotency, reconciliation) against a simulator.

## 5. Sources consulted

- pawaPay: [homepage](https://www.pawapay.io/), [markets](https://www.pawapay.io/markets), [docs](https://docs.pawapay.io/), [providers](https://docs.pawapay.io/v2/docs/providers.md), [refunds](https://docs.pawapay.io/v2/docs/refunds.md), [signatures](https://docs.pawapay.io/v2/docs/signatures.md), [deposits](https://docs.pawapay.io/v2/docs/deposits.md), [test numbers](https://docs.pawapay.io/v2/docs/test_numbers.md), [Gabon launch post](https://pawapay.io/blog/pawapay-live-in-gabon) (listed, not read).
- Moneroo: [coverage](https://moneroo.io/en/coverage), [docs](https://docs.moneroo.io), [webhooks](https://docs.moneroo.io/introduction/webhooks.md), [payment methods](https://docs.moneroo.io/payments/available-methods.md), [testing](https://docs.moneroo.io/payments/testing.md).
- Market overviews (third-party): [PayAtlas Gabon](https://payatlas.com/countries/gabon-ga), [Rafiki Gabon](https://docs.rafiki-api.com/docs/country-coverage-africa-gabon), [Boldrails Airtel Money API](https://boldrails.com/payments/airtel-money-api), [Genuka Pay](https://pay.genuka.com/en), [Nilos market map](https://www.nilos.io/blog/payment-providers-in-africa).
- Airtel developer portal (referenced by third parties, not read): developers.airtel.africa.
