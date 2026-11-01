# Platform Administration Dashboard (Next.js + TypeScript)

Directory: `apps/admin-dashboard`. Audience: Zella Stay staff. Consumes `/api/v1/admin/**`. Hardened differently from other apps: separate deployment and domain, network restrictions (VPN/IP allow-list `[OD-15]`), mandatory second factor, short sessions, no public indexing, strict CSP, all actions audited.

## 1. Admin roles

| Role | Capabilities |
|---|---|
| Super Admin | Manage admin accounts and roles, all configuration, audit access |
| Verification Reviewer | Verification queue, document viewing, approve/reject with reasons, property moderation |
| Support Agent | Customer/vendor/booking lookup (masked by default, reveal is audited), tickets, disputes, permitted booking interventions |
| Finance Admin | Fee/plan/tax configuration, payments, refunds (with approval thresholds), reconciliation, settlements, subscription revenue |
| Content Manager (optional) | Notification templates, amenities, attractions, legal text versions |

Roles and permissions are data-driven and enforced server-side; the UI mirrors them.

## 2. Modules

1. **Verification:** queue with SLA timers, filters (type, country, age), claim/assign, document viewer (watermarked, signed short-lived URLs, access audited), checklist per `cfg_document_requirement`, decision form with reason codes and notes, resubmission history diff.
2. **Organizations & properties:** search, state changes (suspend/reinstate/close), impact preview (open bookings affected), moderation of content and media.
3. **Users:** customers, vendor users, admin accounts: search, status, sessions, forced sign-out, anonymization workflow initiation `[LEGAL]`.
4. **Bookings & support:** booking inspector with timeline (status history, payments, refunds, notifications, chat access with reason), disputes and tickets with SLA, resolution actions (refund, penalty, dismiss).
5. **Finance:** payments explorer, refund queue (manual/failed), reconciliation runs and exceptions with resolution notes, settlements, ledger viewer, service-fee revenue and subscription revenue dashboards, exports.
6. **Subscriptions:** plans CRUD (prices per currency, entitlements: limits and feature flags, trial/grace), subscriber list and states, manual adjustments (audited).
7. **Platform configuration:** countries, currencies, locales, time zones, taxes, service-fee rules, document requirements, payment methods per country, platform parameters (hold windows, chat windows, thresholds), feature flags. Every change is versioned with effective dates, requires confirmation with change summary, and is audit-logged. Dangerous changes require a second approver.
8. **Notifications:** template editor (fr/en, preview, publish workflow), delivery monitoring (search deliveries, dead-letters, manual retry), provider health.
9. **Catalog reference data:** amenities, property types, attractions, translations.
10. **Reviews moderation:** flagged reviews queue, hide/restore with reasons.
11. **Promotions & loyalty:** platform campaigns, promo codes, loyalty rules, abuse monitoring.
12. **Reports & health:** GMV, bookings, conversion, revenue, verification throughput, SLA breaches, outbox/queue health summaries (deep metrics in observability stack).
13. **Audit log viewer:** filter by actor, subject, action, date; export restricted.

## 3. Architecture

Mandatory shared standards: [WEB_ARCHITECTURE.md](WEB_ARCHITECTURE.md) and [ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md) §5. Verification documents open only through short-lived signed URLs requested per view and audited ([MEDIA_ARCHITECTURE.md](../architecture/MEDIA_ARCHITECTURE.md) §4).

Same stack as the other web apps ([PUBLIC_WEBSITE.md](PUBLIC_WEBSITE.md)). Admin UI is English and French (`fr` default). Heavy tables use server-side pagination and saved filters. Destructive or financial actions use a confirmation pattern showing exact impact, requiring a reason field, and use `Idempotency-Key`. Sensitive data is masked by default; "reveal" requires reason and creates an audit event.

## 4. Security requirements

Dedicated admin audience tokens; WebAuthn/TOTP; session timeout and re-authentication for high-risk actions; permission-gated routes server- and client-side; CSRF protections for cookie sessions; no third-party scripts/analytics; rate limiting; anomaly alerts on admin logins; break-glass access procedure documented and audited.

## 5. Testing

Role-matrix E2E tests; audit event assertions for every privileged action; four-eyes workflows; configuration change versioning tests; accessibility and keyboard operation for high-throughput queues. See [TESTING_STRATEGY.md](../engineering/TESTING_STRATEGY.md).
