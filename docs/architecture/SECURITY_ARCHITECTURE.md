# Security Architecture

Baseline: OWASP ASVS Level 2, OWASP API Security Top 10, secure-by-default configuration. Legal and regulatory statements are intentionally absent; items needing validation are tagged `[LEGAL]` and collected in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md).

## 1. Principals and authorization boundaries

| Principal | Token audience | Scope of access |
|---|---|---|
| Anonymous | none | `/public/**` read-only reference and search |
| Customer | `zs-customer` | Own account, own bookings, own chats, own reviews |
| Guest | booking access token | Exactly one booking: view, cancel, pay, chat |
| Vendor user | `zs-vendor` | Resources of organizations they are an ACTIVE member of, restricted by role and property scope |
| Platform admin | `zs-admin` | Cross-tenant, by admin role; always audited |
| Provider (webhook) | signature | Only its webhook endpoint |
| System | internal | Scheduled jobs, listeners; no external credentials |

Boundary rules:
1. **Separate filter chains and audiences.** A customer token is rejected by vendor/admin chains and vice versa, even for the same person. Working assumption: a person who is both a traveller and a vendor user holds two distinct accounts (account kind is immutable) `[OD-9]`.
2. **Authorization is enforced in the application layer** (service methods via method security or explicit policy objects), not only at routes. Controllers cannot be the only barrier.
3. **Deny by default:** endpoints without an explicit rule are denied (checked by a test enumerating all mappings).
4. **Object-level authorization (BOLA) on every ID in a request**: load by `(id, organization_id)` / `(id, account_id)`; mismatch returns 404.
5. Admin endpoints require MFA-verified sessions and are IP/network restrictable.

## 2. Vendor permission matrix

`●` full · `○` read-only · `—` none. Property-scoped members apply this to their assigned properties only.

| Capability | Owner | Administrator | Manager | Receptionist | Accountant |
|---|---|---|---|---|---|
| Organization profile, legal docs | ● | ● | — | — | — |
| Staff & roles | ● | ● (not Owner) | — | — | — |
| Transfer/close organization | ● | — | — | — | — |
| Subscription & billing | ● | ○ | — | — | ○ |
| Create/edit properties, media, amenities | ● | ● | ● | — | — |
| Publish/unpublish | ● | ● | ● | — | — |
| Unit types, calendar, blocks | ● | ● | ● | ○ | — |
| Rates, fees, policies | ● | ● | ● | — | — |
| View bookings | ● | ● | ● | ● | ○ |
| Approve/decline requests, cancel booking | ● | ● | ● | — | — |
| Check-in, no-show | ● | ● | ● | ● | — |
| Chat with guests | ● | ● | ● | ● | — |
| Respond to reviews | ● | ● | ● | ○ | — |
| Promotions | ● | ● | ● | — | — |
| Financial reports, settlements, invoices | ● | ● | ○ | — | ● |
| Guest personal data (full contact) | ● | ● | ● | ● | — (masked) |

Permissions are modeled as fine-grained permission strings (`booking.approve`, `rate.edit`, …) mapped to roles in `organization`; the matrix is the default mapping, stored as data and covered by tests. Custom roles are out of scope `[OD-9]`.

## 3. Authentication

- Passwords: Argon2id (or bcrypt cost ≥ 12 if Argon2 unavailable), breached-password check, no composition rules beyond length ≥ 10.
- Phone/email verification with hashed, short-lived, attempt-limited codes; SMS pumping protection (per-number and per-IP limits, country allow-list).
- JWT access tokens: ≤ 15 min, ES256 signing (ADR-31; rotatable keys, `kid`), audience-bound, minimal claims (subject, audience, session id, org memberships summary not authoritative for writes — permissions re-checked from DB or short cache).
- Refresh tokens: opaque, hashed at rest, rotating, reuse detection revokes the family; bound to device record.
- Admin: mandatory TOTP/WebAuthn second factor; short sessions; no self-registration.
- Account lockout/backoff on repeated failures; uniform error messages (no user enumeration).
- Implemented in Slice A: ES256 access tokens (ADR-31), rotating refresh tokens with reuse detection, per-audience filter chains with deny-by-default routes and a fixed-window per-address rate limiter (ADR-34), progressive account lockout, uniform failures, TOTP admin second factor with encrypted seeds and recovery codes, and the audit trail (ADR-32). Lifetimes and thresholds are typed configuration.
- Identity is in-house (ADR-30). Sessions, lockout, verification and reset rules, the admin second-factor step and the audit events are specified in docs/superpowers/specs/2026-11-02-phase1-slice-a-identity-audit-design.md; values (lifetimes, thresholds) are typed configuration, not code.

## 4. Data protection in transit and at rest

- TLS 1.2+ everywhere; HSTS on web apps; certificate pinning in mobile is optional hardening decided later.
- PostgreSQL encrypted at rest; media at rest is encrypted by the provider (Cloudinary), whose security posture, sub-processors and data location are reviewed under L-18. Application-level encryption for the most sensitive fields (identity document numbers, if stored) with envelope encryption and key rotation `[LEGAL]` for what may be stored.
- Secrets only from environment/secret manager; none in the repository, images, or CI logs. Pre-commit and CI secret scanning.
- Private documents and chat images: Cloudinary `authenticated` delivery only, never public; signed URLs generated server-side after authorization, expiry ≤ 5 minutes, every document access audited. The Cloudinary API secret exists only in the backend (secret manager/mounted file) and is never sent to, built into or logged by any frontend; scanned by the standards checker. Verification documents are not uploaded to production until L-18 is closed.
- Error responses never expose stack traces, exception types, SQL, constraint names, hostnames or rejected values; unexpected errors return a generic code with a request id ([ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md)).
- Card data: **never touches our servers.** Card entry via provider-hosted fields/redirect (tokenization) to stay out of PCI scope beyond the lowest SAQ tier; validated with the chosen PSP `[LEGAL]`.

## 5. Application security controls

- Input validation (allow-list), output encoding (web apps), parameterized queries only (no string-built SQL), safe file handling.
- Upload pipeline ([MEDIA_ARCHITECTURE.md](MEDIA_ARCHITECTURE.md)): server-proxied uploads for documents/avatars/chat images with size, extension, magic-number checks, image re-encoding (strips EXIF GPS), malware scan before storage, server-chosen random public IDs; signed direct uploads for galleries/videos with every parameter signed and size verified server-side after upload; user content is served from the media domain, never the app origin.
- CSRF: not applicable to bearer-token APIs; web apps store tokens per [PUBLIC_WEBSITE.md](../frontend/PUBLIC_WEBSITE.md) (httpOnly secure cookies via BFF route handlers recommended) with SameSite and CSRF tokens where cookies are used.
- CORS: explicit origin allow-list per environment; no wildcard with credentials.
- Security headers: CSP (nonce-based), `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, frame-ancestors none.
- Rate limiting and bot protection on registration, login, OTP, search scraping, promo code validation (brute-force of codes), and booking creation.
- SSRF: no user-supplied URL fetches server-side; webhook endpoints do not fetch URLs from payloads.
- Dependency management: lockfiles, automated vulnerability scanning (SCA), container image scanning, SBOM in CI, pinned base images.
- Logging hygiene: no secrets, tokens, card data, OTPs, or full documents in logs; PII masked.

## 6. Payment-specific security

Idempotency, webhook signature verification, timing-safe comparison, replay window, amount/currency re-verification against the server-side record (never trust callback amounts), least-privilege provider credentials per environment, separation of duties for manual refunds. See [PAYMENT_ARCHITECTURE.md](PAYMENT_ARCHITECTURE.md).

## 7. Multi-tenant isolation

See [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) §10. Additional controls: tenant-scoped cache keys (`org:{id}:…`), tenant id in log context, tenant-scoped object keys, export endpoints re-authorize at generation time.

## 8. Audit logging

- Recorded via `audit` module API from application services (not from controllers), including: authentication events (success/failure/lockout), role and permission changes, verification decisions, document access, property approval/suspension, configuration changes (fees, plans, taxes), manual financial actions (refund, settlement override, reconciliation resolution), admin impersonation/support access to customer or vendor data, data export/erasure, security setting changes.
- Fields: actor, acting organization, action, subject, outcome, IP, user agent, correlation id, before/after summary (no secrets).
- Append-only at the database privilege level; retention class `[LEGAL]`; optional hash chaining for tamper evidence `[OD-17]`.
- Audit recording failure for privileged actions fails the action (fail closed); for low-risk events it is logged and alerted.

## 9. Threat model highlights (to be expanded per phase)

| Threat | Mitigation |
|---|---|
| Cross-tenant data access | §7, RLS, authorization test matrix |
| Double spend / duplicate payment | Idempotency, unique constraints, webhook dedupe |
| Inventory race / overbooking | DB constraints and locks ([INVENTORY_ARCHITECTURE.md](INVENTORY_ARCHITECTURE.md)) |
| Price tampering | Server-side quote; client sends only `quoteId` |
| Account takeover | MFA for admin, device-bound refresh tokens, OTP rate limiting, breach checks |
| Fake listings / fraud | Mandatory verification, document review, moderation, suspension, reporting |
| Chat abuse (spam, off-platform solicitation) | Booking-bound conversations, rate limits, report/block, content moderation hooks |
| Promo code abuse | Rate limits, per-customer limits, redemption ledger with unique constraints |
| Enumeration/scraping | Rate limits, opaque ids, bot protection |
| Insider misuse | RBAC, least privilege, audit, four-eyes for high-risk finance actions |
| Webhook forgery | Signatures, allow-lists, server-side status re-query for critical states |
| Malicious uploads | §5 upload pipeline |

## 10. Data privacy and regulatory (analysis frame — no legal conclusions)

Data classification: **Public** (listing content), **Internal**, **Personal** (name, email, phone, booking history, chat), **Sensitive** (identity documents, business registration documents, payment references), **Secret** (credentials, keys).

Engineering controls to implement regardless of jurisdiction: data minimization, purpose-bound fields, consent records (marketing, notification channels), access logging, export and erasure workflows, retention schedules, pseudonymization in analytics, breach response runbook, subprocessor inventory (SMS, email, push, PSP, hosting, maps).

Requires validation by qualified local counsel before launch (all `[LEGAL]`):
1. Applicable personal-data protection law(s) and authority in Gabon (and for each future country), including lawful basis, cross-border transfer rules and **data residency** (affects hosting region `[OD-15]`).
2. Retention periods for financial records, audit logs, identity documents, chat.
3. Rules on collecting and storing identity documents of hosts and of guests (including any guest-registration duty toward authorities).
4. Payment regulation (**mandatory L-1**): written confirmation that the chosen marketplace provider holds every authorization needed in Gabon to collect and settle funds, and that Zella Stay as a non-custodial platform needs none of its own (ADR-20); card-scheme and PSP requirements; mobile-money provider agreements.
5. Tax: VAT/tourism taxes on accommodation, service fees and subscriptions; invoicing requirements; who collects and remits.
6. Short-term rental and property classification rules; platform liability and terms of service.
7. Consumer-protection rules: cancellation/refund disclosures, price display, review authenticity.
8. Electronic communications rules for SMS/marketing consent.
9. Minors' data and age restrictions.
10. (L-18) Use of Cloudinary and its sub-processors for identity and business documents: DPA, storage region (US by default; EU only on Enterprise per provider documentation), retention after deletion, backup settings.

## 11. Incident response

Runbooks (to be written in Phase 0) for: credential leak, suspected tenant breach, payment discrepancy, provider compromise, data breach notification process (timelines `[LEGAL]`). Security contact and disclosure policy published before launch.
