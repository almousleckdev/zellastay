# Feature Catalog

Feature → owning module → application surface → phase. Phases are defined in [ROADMAP.md](ROADMAP.md). Requirement IDs refer to [PRD.md](PRD.md). Module names refer to [MODULAR_ARCHITECTURE.md](../architecture/MODULAR_ARCHITECTURE.md).

Legend: **C** customer mobile · **W** public website · **V** vendor dashboard · **A** admin dashboard.

| Feature | Req | Module | Surfaces | Phase |
|---|---|---|---|---|
| Customer registration/login (email, phone) | FR-ID-1 | identity | C W | 1 |
| Guest checkout and booking access token | FR-ID-2 | identity, booking | C W | 2 |
| Vendor self-registration (individual/business) | FR-VO-1 | onboarding | W | 1 |
| Document upload and private storage | FR-VO-2 | onboarding, media | W V | 1 |
| Verification queue, approve/reject with reason | FR-VO-4/5 | onboarding | A | 1 |
| Organization, staff, roles, property scoping | FR-VM-* | organization | V | 1 |
| Property creation, galleries, amenities, rules | FR-CT-* | catalog, media | V | 1 |
| Property moderation | FR-VO-4 | catalog, onboarding | A | 1 |
| Room types, rooms, units, calendars | FR-IN-1/2 | inventory | V | 2 |
| Seasonal and rule-based pricing | FR-IN-4 | pricing | V | 2 |
| Search, filters, map | FR-SR-* | search | C W | 2 |
| Favorites and wishlists | FR-SR-3 | engagement | C W | 2 |
| Instant Booking | FR-BK-1 | booking | C W | 2 |
| Request to Book | FR-BK-1 | booking | C W V | 2 |
| Cancellation policies and refunds | FR-BK-3 | booking, payment | C W V A | 2 |
| Payments: Airtel Money, Moov Money, cards | FR-PY-* | payment | C W | 2 |
| Reconciliation and payment oversight | FR-PY-2 | payment | A | 2 |
| Subscription plans, billing, entitlements | FR-SB-* | subscription | V A | 2 |
| Customer service fee configuration | FR-SB-2 | pricing, platformconfig | A | 2 |
| Notifications (push/email/SMS/in-app), templates, preferences | FR-NT-* | notification | C W V A | 1–2 |
| Chat (text, images, receipts, unread) | FR-CH-* | chat | C V | 3 |
| Reviews and ratings | FR-RV-1 | review | C W V A | 3 |
| Support tickets, disputes, complaints | FR-SP-1 | support | C W V A | 3 |
| Promo codes, vouchers, seasonal campaigns | FR-LY-1 | promotion | C W A | 4 |
| Loyalty points, rewards, referrals, reward history | FR-LY-1 | loyalty | C W A | 4 |
| Vendor promotional campaigns | FR-LY-2 | promotion | V A | 4 |
| Vendor financial reports | FR-RP-1 | reporting | V | 3 |
| Admin reports and dashboards | FR-RP-2 | reporting | A | 3 |
| Audit log viewer | NFR-9 | audit | A | 1 |
| Country/currency/tax/language configuration | PRD §1 | platformconfig | A | 1 |
| iCal/channel sync | FR-IN-5 | inventory | V | Future `[OD-12]` |
| WhatsApp notifications | FR-NT-1 | notification | — | Future |
| Additional locales, countries | PRD §1 | platformconfig | all | Future |

## Feature acceptance rule

A feature is "done" only when: documentation updated, tests (unit + integration; E2E for critical flows) pass, authorization rules covered by tests, audit events emitted where required, i18n keys present in French and English, observability (logs/metrics) added, and a code review per [REVIEW.md](../engineering/REVIEW.md) is complete.
