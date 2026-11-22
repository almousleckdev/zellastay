# Vendor Dashboard (Next.js + TypeScript)

Directory: `web/vendor`. Audience: staff of vendor organizations (Owner, Administrator, Manager, Receptionist, Accountant). Consumes `/api/v1/vendor/**` and `/ws`. Authentication is separate from the customer apps (vendor token audience).

## 1. Information architecture

| Area | Content | Roles (default, see [SECURITY_ARCHITECTURE.md](../architecture/SECURITY_ARCHITECTURE.md) §2) |
|---|---|---|
| Home | Today's arrivals/departures, pending requests, unread messages, alerts (subscription state, verification status) | all (filtered) |
| Onboarding status | Verification case status, rejection reasons, resubmission | Owner, Administrator |
| Properties | List, create/edit wizard, media manager, amenities, rules, check-in instructions, booking mode, cancellation policy, publish/unpublish | Owner, Administrator, Manager |
| Inventory | Unit types, units, calendar (month/timeline), bulk edit, blocks | Owner, Administrator, Manager (Receptionist view) |
| Pricing | Rate plans, seasonal rules, fees, taxes preview | Owner, Administrator, Manager |
| Reservations | List/filters, detail, approve/decline, cancel, check-in, no-show, notes | by permission |
| Messages | Conversation inbox per property, attachments | Owner, Administrator, Manager, Receptionist |
| Reviews | List and responses | Owner, Administrator, Manager |
| Promotions | Campaigns within plan and platform rules | Owner, Administrator, Manager |
| Finance | Reports, settlements, invoices, exports | Owner, Administrator, Accountant |
| Subscription | Plan, usage vs limits, upgrade/downgrade, invoices, payment | Owner (Accountant read) |
| Team | Staff, roles, property scopes, invitations | Owner, Administrator |
| Settings | Organization profile, notification preferences, language | by role |

Navigation is generated from the user's effective permissions delivered by the API; the UI hides what the user cannot do but **never relies on hiding for security**. Organization switcher for users with multiple memberships; property switcher for scoped users.

## 2. Architecture

Mandatory shared standards: [WEB_ARCHITECTURE.md](WEB_ARCHITECTURE.md) and [ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md) §5. Property photos and videos use backend-issued signed uploads with upload progress, per-file validation feedback and server-side rejection handling ([MEDIA_ARCHITECTURE.md](../architecture/MEDIA_ARCHITECTURE.md) §3.2); verification documents are uploaded through the server-proxied flow.

Same stack and conventions as the public website ([PUBLIC_WEBSITE.md](PUBLIC_WEBSITE.md)): Next.js App Router, TypeScript strict, generated API client, TanStack Query, BFF cookie session, `next-intl` (fr default, en), shared `packages/ui`.

Differences:
- Mostly client-rendered behind authentication; server components for shell and data prefetch.
- Complex data UIs: calendar grid (virtualized timeline), data tables with server-side pagination/filter/sort, bulk editors with preview-before-apply and conflict reporting from the API (cannot block dates with existing bookings).
- Optimistic concurrency: edits send `If-Match` ETag; stale edit shows a merge/reload dialog.
- Real-time: STOMP for new requests, chat, booking updates; badges/toasts; reconnect handling.
- Entitlement awareness: the API returns entitlements and limits; the UI shows locked features with upgrade prompts, enforcement remains server-side. Subscription `PAST_DUE`/`SUSPENDED` states display banners and restrict actions consistent with [BUSINESS_MODEL.md](../product/BUSINESS_MODEL.md) §3.3.
- Exports (CSV/PDF) are generated server-side as async jobs when large; links are signed and expire.
- Tenant safety: all route params include organization/property context validated by the API; tenant-scoped cache keys in client query cache; switching organization clears cache.
- Security: same controls as public site; sensitive guest data masked per role (Accountant); document viewing via signed URLs.

## 3. Key workflows (UI requirements)

1. **Property creation wizard** with autosave drafts, per-step validation, completeness checklist matching verification requirements, localized content tabs (fr/en) with missing-translation indicators, media upload with progress/resume and ordering, map pin placement with address geocoding.
2. **Calendar & rates**: month and timeline views; select range → set availability/price/min-stay/closures; clear display of held vs booked vs blocked.
3. **Request handling**: queue sorted by deadline, countdown to `respond_by`, approve/decline with reason, quick message.
4. **Check-in desk** (Receptionist): arrivals list, search by reference/name, check-in, no-show, message guest.
5. **Finance**: filters by property/date, drill-down to booking, refunds visible, export; reconciliation with settlements.
6. **Team management**: invite by email, assign role and property scope, deactivate; effective permissions preview.

## 4. Non-functional

Desktop-first, responsive down to tablet (receptionist use on tablets/phones for check-in); keyboard shortcuts for desk operations; accessibility WCAG 2.2 AA; performance budget on mid-range hardware and slow networks; all strings localized.

## 5. Testing

Component tests, Playwright E2E per role (permission matrix verified from the UI: role X cannot see/act on Y), tenant isolation E2E (two orgs), calendar edge cases (time zones, month boundaries), accessibility checks. See [TESTING_STRATEGY.md](../engineering/TESTING_STRATEGY.md).
