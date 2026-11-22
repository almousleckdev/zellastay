# Phase 1, Slice A — Identity and Audit: Design (for review)

Status: **approved and implemented (A10, the entitlement contract, is built with Slice B).** Decisions `P-1`..`P-8` were approved as recommended and are recorded as ADR-30..ADR-33 (P-1..P-6 and P-8 in ADR-30/ADR-32, P-7 in ADR-33). Everything else restates the approved documents: [SECURITY_ARCHITECTURE.md](../../architecture/SECURITY_ARCHITECTURE.md), [API_SPECIFICATION.md](../../architecture/API_SPECIFICATION.md), [DATABASE_DESIGN.md](../../architecture/DATABASE_DESIGN.md), [MODULAR_ARCHITECTURE.md](../../architecture/MODULAR_ARCHITECTURE.md), [ERROR_HANDLING.md](../../engineering/ERROR_HANDLING.md), PRD FR-ID-1..4. The implementation plan is [the Slice A plan](../plans/2026-11-02-phase1-slice-a-identity-audit-plan.md).

## 1. Intent and boundaries

In-house identity (OD-10) and a tamper-resistant audit trail, so that every later slice can authenticate callers, authorize by audience, and record privileged actions.

**In scope**
- Accounts of three immutable kinds: `CUSTOMER`, `VENDOR_USER`, `ADMIN`.
- Registration (customer, vendor user), email verification, login, refresh, logout, sessions, password change and recovery.
- Admin accounts: bootstrap, TOTP second factor, recovery codes, admin roles, account suspend/reinstate.
- Spring Security filter chains per audience, deny-by-default, rate limiting and lockout.
- Audit module: recorder API, append-only store, admin audit viewer.

**Non-goals (deferred, with owner)**
- Organization creation, staff invitations, roles (Slice B; identity only supplies the account).
- Phone/SMS authentication: needs an SMS provider (OD-20 unselected). Phone is stored, never used as a login or verification channel in Slice A.
- Social login, passkeys/WebAuthn (FR-ID-5), guest booking access tokens (Phase 2 booking), devices/push tokens (notification phase), avatars (media, Slice F), custom vendor roles (OD-9).
- Real email delivery (notification module, see §9 and P-3).

**Anti-goals:** user enumeration, any token or code stored in plaintext, authorization in controllers only, identity knowing about organizations.

## 2. Module placement and dependencies

No change to the approved graph (§4 of the modular document):

```
identity → shared, platformconfig, audit        audit → shared
```

- Identity must not depend on `organization`, `media`, `onboarding` (it never needs them). Organization (and later modules) read identity only through `identity.api`.
- `notification → identity` already exists: notification implements identity's outbound port (P-3), which is why no new edge is required.
- `audit` is a leaf. Its admin viewer authorizes with a plain Spring Security authority (`audit.read`) granted by identity's filter chain, so audit never imports identity.
- Cross-cutting web pieces live in `shared.web` (already open): the rate-limit filter and the problem-details entry points (reuse `ProblemFactory`). Security chains and token code live in `identity` (`web`/`infrastructure`).

Layout (each package follows the layering rules; ArchUnit enforces):

```
identity/api            AccountId, AccountKind, AccountSummary, IdentityApi, CurrentPrincipal,
                        AdminAccessApi, AdminPermission, VerificationDelivery (port), events/*
identity/domain         Account, AccountStatus, Email, PasswordPolicy, Session, RefreshTokenFamily,
                        VerificationChallenge, PasswordResetToken, AdminRole, repositories (interfaces)
identity/application    RegisterAccount, VerifyEmail, Login, RefreshSession, Logout, ChangePassword,
                        RequestPasswordReset, ResetPassword, EnrollTotp, VerifyTotp, SuspendAccount, ...
identity/infrastructure JDBC repositories, Argon2id hasher, JWT issuer/validator, TOTP, secret loading,
                        Redis session cache, bootstrap runner
identity/web            controllers + DTOs, SecurityFilterChains per audience, entry points
audit/api               AuditRecorder, AuditEntry, AuditActor, AuditOutcome, AuditAction (catalog)
audit/domain|application|infrastructure|web   store, partition maintenance, query, admin viewer
```

## 3. Account model and lifecycle

Tables (module prefix `ident_`, per DATABASE_DESIGN; types refined here):

- `ident_account(id uuid, kind, email, email_normalized, email_verified_at, phone_e164 null, password_hash, status, locale, country, failed_login_count, locked_until, password_changed_at, last_login_at, created_at, version)` — unique `(kind, email_normalized)`. Account kind is immutable; a person who is both traveller and vendor holds two accounts (OD-9 working assumption), hence uniqueness is per kind.
- `ident_profile(account_id, first_name, last_name)` — only fields needed now; extra fields wait for L-5/L-14.
- `ident_session(id, account_id, family_id, audience, created_at, last_used_at, absolute_expires_at, revoked_at, revoked_reason, user_agent_hash, ip_prefix)` and `ident_refresh_token(id, family_id, token_hash, issued_at, expires_at, used_at, replaced_by)`. The existing sketch has only the token table; a session row is added so `sid` in the access token can be revoked and listed (P-2).
- `ident_challenge(id, account_id, purpose {EMAIL_VERIFICATION, PASSWORD_RESET}, code_hash, expires_at, attempts, consumed_at, created_at)` — replaces `ident_verification_code`.
- `ident_admin_role(account_id, role)`, `ident_admin_mfa(account_id, secret_ciphertext, enrolled_at, last_used_step)`, `ident_recovery_code(account_id, code_hash, used_at)`.
- Credential events go to `audit` (no separate `ident_credential_event` table).

States of `ident_account.status`:

| State | Meaning | Entered by | Can authenticate |
|---|---|---|---|
| `PENDING_VERIFICATION` | Registered, email not verified | register | No (login returns the generic failure; resend allowed) |
| `ACTIVE` | Normal | email verified; admin: first MFA enrolment complete | Yes |
| `SUSPENDED` | Blocked by an admin | admin action (reason code) | No; all sessions revoked immediately |
| `DEACTIVATED` | Closed by the user | user request (password re-entry) | No; reversible only by support within a retention window `[LEGAL]` |
| `ANONYMIZED` | Personal data erased | erasure workflow (L-5/L-7), terminal | No |

Temporary lockout is **not** a status: `locked_until` is a time-bounded condition that expires on its own.
Transitions are methods on the `Account` aggregate that reject illegal moves with identity-namespaced exceptions; there is no setter for `status`.

## 4. Authentication

**Endpoints (existing API spec, same paths):** `POST /api/v1/auth/{register,verify,login,refresh,logout,password/forgot,password/reset}`, `/api/v1/vendor/auth/{register,login}`, `/api/v1/admin/auth/login` (+ `/admin/auth/mfa/verify`), `/api/v1/me/password`, `/api/v1/me/sessions`. Additions to the spec (to be documented): resend verification, change password, session list/revoke, MFA enrol/verify, admin account suspend/reinstate.

- **Password policy:** length 10–128 after Unicode NFKC normalization; no composition rules; rejected when in the common-password denylist or equal to the email local part. Breached-password lookup: see P-4.
- **Hashing:** Argon2id (Spring Security `Argon2PasswordEncoder`, BouncyCastle), parameters tuned to ~250 ms on production-like hardware and held in typed configuration; stored with the parameters inside the hash string so they can be raised later (rehash on login when below current parameters). Fallback to bcrypt cost ≥ 12 only through an ADR.
- **Login:** identical response and timing for unknown email, wrong password, unverified or non-active account (a dummy hash verification equalizes timing). Only after a correct password do we reveal `identity.account-suspended`. Failed attempts: progressive lockout per account (e.g. 5 failures → 1 min doubling up to a cap; values are configuration) plus per-IP and per-account rate limits (§8). Successful login resets the counter and writes `last_login_at`.
- **Admin login:** password, then TOTP in a second step. The first step returns a short-lived, single-purpose `mfa_pending` token (audience `zs-admin-mfa`, 5 min, usable only on the MFA endpoint); only the second step issues a full admin token with `amr` containing `mfa`. Admin tokens and sessions are shorter than the others (values in configuration).
- **Registration without enumeration:** `POST /auth/register` always answers `202` with the same body. If the email is new a verification message is requested; if it already exists, the existing account owner is told by message instead ("someone tried to register"). Concurrent duplicates are resolved by the unique constraint, mapped to the same neutral response.
- **Email verification:** a 6-digit code (CSPRNG), HMAC-SHA-256 with a server pepper stored in `code_hash` (plain hashing is brute-forceable at 10^6 values), TTL 10 min, 5 attempts then the challenge is burned, resend cooldown 60 s, at most 5 sends/hour/account and per IP. Verifying marks the account `ACTIVE`. The code is never logged, stored in plaintext or placed in an event (P-3).
- **Password recovery:** `forgot` always answers `202`. The reset credential is a 256-bit random single-use token, SHA-256 hashed at rest, 30 min TTL, delivered as a link. `reset` consumes it, applies the policy, sets the new hash, **revokes every session of the account**, and records the audit event. An unverified account's reset also verifies the email.
- **Change password:** requires the current password; revokes all other sessions; keeps the current one.
- **Email change** is out of Slice A (separate, requires re-verification of the new address; documented as deferred).

## 5. Token lifecycle

| Item | Design |
|---|---|
| Access token | JWT, signed with ES256 (ADR-31; EdDSA was rejected after the A1 spike because Nimbus needs the extra Tink library), header `kid`, claims `iss`, `sub`=accountId, `aud` (`zs-customer`/`zs-vendor`/`zs-admin`), `sid`, `iat`, `exp`, `jti`, `amr`. No organization or permission claims. Lifetime ≤ 15 min (configuration). |
| Keys | Private/public keys from `ZS_SECRETS_DIR`/environment (never the repo). A key ring supports several verification keys so rotation is: add new key → start signing with it → retire the old one after the access-token lifetime. JWKS endpoint is not exposed (single backend). |
| Refresh token | 256-bit opaque random value, returned once, stored SHA-256 hashed, rotated on every use. Each rotation links `replaced_by`. Presenting an already-used token is reuse: the entire family/session is revoked, an `auth.refresh-reuse-detected` audit event is written, and the legitimate client must log in again. A small concurrency window (configurable, seconds) tolerates a client retry of the same refresh without triggering reuse. |
| Session | One row per login. `absolute_expires_at` and an idle timeout are audience-specific configuration (customer longest, admin shortest). Refresh fails after either. Users can list and revoke their own sessions; admins can revoke any account's sessions. |
| Revocation propagation | Each request validates signature, `aud`, `exp`, then checks the session/account is still active through a Redis cache keyed by `sid` with a short TTL (≤ 60 s), falling back to the database; Redis failure falls back to the database (never fail-open). Suspension, logout, password change and reuse detection write the revocation and evict the cache entry. |
| Logout | Revokes the session (idempotent). `logout-all` revokes the account's sessions. |
| Storage guidance for clients | Refresh tokens are not kept in `localStorage`; dashboards use the httpOnly-cookie backend-for-frontend pattern, mobile uses platform secure storage. Documented now, applied when the frontends are scaffolded. |

## 6. Authorization boundaries

1. **Audience-separated filter chains** by route prefix (`/api/v1/public/**` anonymous; customer resources; `/api/v1/vendor/**`; `/api/v1/admin/**`), each with its own audience check, rate-limit policy and entry points. A token for one audience is rejected by the others (`auth.forbidden`/`auth.unauthenticated`).
2. **Deny by default:** the final matcher of every chain is `denyAll`. A test enumerates every mapped handler (route catalog) and fails if one lacks an explicit rule. Later slices register their routes into the same catalog and add their allowed/denied/cross-tenant tests.
3. **Authorization in the application layer:** services call a policy (`@PreAuthorize` with typed permission checks or explicit policy objects), never only the route matcher. Controllers contain no authorization logic.
4. **Principal contract:** `identity.api.CurrentPrincipal` (accountId, kind, audience, sessionId, mfaVerified) is the only thing other modules receive. Tenant/organization context is resolved by `organization` (`VendorAccessApi`, Slice B) from `(accountId, organizationId)`; the organization id arrives as a path segment or a validated header, never from a token claim.
5. **Admin permissions:** admin roles per ADMIN_DASHBOARD §1 (Super Admin, Support Agent, Finance Admin and the moderator/reviewer roles listed there) are stored as data (`ident_admin_role`) with a role → permission mapping table seeded by migration (reference data, not business data). `AdminAccessApi.require(principal, AdminPermission)` is how other modules authorize admin actions; `/admin/**` also needs `mfaVerified`. Slice A seeds the permissions it needs (`account.suspend`, `account.reinstate`, `audit.read`, `admin.manage`) and later slices add theirs in their own migrations.
6. **No self-registration of admins.** The first Super Admin is created by an explicit, disabled-by-default bootstrap runner (P-5); further admins are created by a Super Admin (audited), and every admin must enrol TOTP before any other admin endpoint works.

## 7. Audit module

**Recorder API (`audit.api`)**: `AuditRecorder.record(AuditEntry)` where `AuditEntry` = actor (type + id + acting organization), action (typed `AuditAction` constant), subject (type + id), outcome (`SUCCESS`/`DENIED`/`FAILURE`), `details` (a bounded map of safe summary values), with IP, user agent and `requestId`/correlation id filled automatically from the request context. Callers are application services, never controllers.

**Two recording modes** (SECURITY §8):
- *Privileged* (suspension, role changes, admin actions): `record` joins the caller's transaction; if it fails, the action fails (fail-closed).
- *Security telemetry* (login failure, lockout, reuse detection): `recordBestEffort` writes in its own transaction; failure is logged and counted, and does not break the request.

**Storage:** `audit_event` as in DATABASE_DESIGN, monthly range-partitioned on `occurred_at`. Immutability is enforced in two ways: (1) a trigger that raises on `UPDATE`/`DELETE`/`TRUNCATE` so it is testable in CI with the single application role, and (2) in deployed environments the runtime role is created without those privileges (documented in DEVOPS; the migration role is separate). A scheduled maintenance job creates the next partitions ahead of time and alerts if it cannot; the default partition catches stragglers and raises an alert. Hash chaining is optional (OD-17) and **not** part of Slice A; the schema leaves room (`prev_hash`, `hash` nullable) only if you want it (P-6).

**Never in an entry:** passwords, codes, tokens, TOTP secrets, full emails (masked) or rejected values. `details` is validated against an allow-list per action by the recorder.

**Read side:** `GET /api/v1/admin/audit-events` (cursor pagination, filters: actor, action, subject, date range) with authority `audit.read`; every use of the viewer is itself audited for non-trivial exports.

**Initial action catalog:** `auth.login.success|failure|lockout`, `auth.logout`, `auth.session.revoked`, `auth.refresh.reuse-detected`, `account.registered`, `account.email-verified`, `account.password-changed`, `account.password-reset-requested|completed`, `account.suspended|reinstated|deactivated`, `admin.mfa.enrolled|verified|failed|recovery-used`, `admin.account.created`, `admin.role.granted|revoked`, `admin.bootstrap.executed`. Later slices add theirs (organization, verification decisions, property approval) to the same catalog.

## 8. Security and abuse-prevention requirements

| Requirement | Mechanism / test |
|---|---|
| Rate limiting, auth strictest | Redis token bucket in `shared.web`, keys per IP and per principal/identifier, policy per endpoint class from configuration; `429` problem+json with `Retry-After`. Redis outage: authentication endpoints fall back to a per-instance in-memory limiter (degraded, never unlimited). |
| Credential stuffing | Per-account progressive lockout, per-IP limits, uniform responses, dummy-hash timing equalization. |
| OTP abuse | Attempt cap, cooldown, hourly caps; email-only in Slice A so SMS pumping is out of surface until OD-20. |
| Secrets | Peppers, JWT keys, TOTP encryption key and bootstrap secret come only from `ZS_SECRETS_DIR`/environment (names added to the DEVOPS configuration contract; `.env.example` names only). TOTP secrets encrypted with AES-GCM using a dedicated key and key id. |
| Token theft | Short access tokens, rotating refresh with reuse detection, session list/revoke, revoke-all on password reset/change. |
| Transport/headers | HSTS, `X-Content-Type-Options`, no framework banners; CORS from an explicit allow-list per audience (empty by default); CSRF not applicable to bearer-header APIs and documented as such. |
| Logging | No secrets, codes, tokens, passwords or full emails in logs; email masked (`a***@d***.com`); all auth logs carry `requestId`. |
| Information disclosure | All errors via `ProblemFactory`; entry points for 401/403 share the contract (ERROR_HANDLING §3). |
| Data minimisation | Only fields in §3; no date of birth; consent and legal fields wait for L-5/L-13/L-14. |
| Dependency hygiene | New security libraries pinned in the version catalog, reviewed for maintenance status, covered by the existing Dependabot configuration. |

Threats consciously accepted for Slice A: no breached-password online lookup unless P-4 is approved; no device binding of refresh tokens (device records arrive with push notifications); no admin network restriction (OD-15, deployment concern).

## 9. Integration contracts

### 9.1 Identity ↔ Organization (consumed in Slice B)
- Organization calls `IdentityApi.findAccount(AccountId) : Optional<AccountSummary>` (id, kind, status, emailVerified, maskedEmail, locale). It additionally needs `IdentityApi.findVendorUserByVerifiedEmail(email)` for staff invitations: an invitation is accepted only by an `ACTIVE`, email-verified `VENDOR_USER` whose verified email equals the invited one.
- Organization consumes `AccountSuspended`/`AccountReinstated`/`AccountDeactivated` events (EVENT_CATALOG lists organization as consumer; `AccountDeactivated` is a proposed addition) to deactivate memberships; an organization whose only Owner is suspended is flagged, not auto-closed.
- Vendor registration is **two steps by design**: `POST /vendor/auth/register` (Slice A) creates and verifies a `VENDOR_USER` account; `POST /vendor/organizations` (Slice B) creates the organization and its Owner membership for an authenticated vendor user. Identity never creates organizations. Individual hosts are organizations with `legal_type = INDIVIDUAL`.
- Organization never reads `ident_*` tables and never stores emails or names of members beyond what the API returns for display.

### 9.2 Identity ↔ Platform Admin
"Platform Admin" is not a module; admin features live as `/admin/**` controllers in the owning modules. Identity provides: admin accounts, roles, MFA, `AdminAccessApi`, and the `ADMIN` chain. Admin endpoints for accounts (list, suspend, reinstate, revoke sessions, create admin) live in identity `web`. All admin mutations audit with `acting` admin id; support access to personal data is masked by default and "reveal" is a separate audited action.

### 9.3 Identity → Notification (port, see P-3)
`identity.api.VerificationDelivery` is an outbound port: `deliver(DeliveryRequest)` with a template key, recipient reference, locale and a **transient** secret payload (code or link). The `notification` module implements it. Identity calls it after commit; failures never roll back the account change and are retried by the user (resend). Alongside, the outbox events listed in EVENT_CATALOG are published without secrets for analytics/consumers. In Slice A the only implementation is a development/test sink (captures the message in memory for tests, prints nothing secret in production profiles; startup in `PRODUCTION` fails if no real implementation is registered).

### 9.4 Events (EVENT_CATALOG changes)
Already listed: `AccountRegistered`, `VerificationCodeRequested`, `PasswordResetRequested`, `AccountSuspended`, `AccountReinstated`. Changes: payloads exclude every secret (the catalog row for `VerificationCodeRequested` is clarified); `AccountDeactivated` added. All carry the common envelope.

### 9.5 Audit contract
Every module that performs a privileged action calls `AuditRecorder` in its application service. `ModuleStructureTest` already allows `audit::api` for each of them.

## 10. Error codes (module namespace `identity.*`; shared `auth.*`, `rate.*` reused)

`auth.unauthenticated`, `auth.forbidden`, `rate.limited` (existing); new: `identity.invalid-credentials` (401, generic), `identity.account-suspended` (403), `identity.verification-invalid` (400/422, wrong or expired code), `identity.verification-attempts-exceeded` (429), `identity.reset-token-invalid` (400), `identity.password-rejected` (400 with field error), `identity.refresh-invalid` (401), `identity.mfa-required` (401), `identity.mfa-invalid` (401), `identity.session-not-found` (404). Each gets French and English text; `ErrorCatalogTest` enforces parity and namespace. Exceptions are `ZellaStayException` subclasses in `identity.api` or `identity.domain`.

## 11. Minimum subscription entitlement contract (for catalog; not billing)

Purpose: `catalog` (and `organization`, `inventory`) must ask "may this organization do X?" without importing subscription internals or knowing plans (ADR-19, BUSINESS_MODEL §3).

```
subscription.api
  EntitlementKey (enum)   MAX_PROPERTIES, MAX_UNITS, MAX_STAFF, MAX_PHOTOS_PER_PROPERTY,
                          MAX_VIDEOS_PER_PROPERTY            (LIMIT kind)
                          INSTANT_BOOKING                    (FLAG kind; others added with their phase)
  EntitlementApi
    EntitlementDecision checkLimit(OrganizationId, EntitlementKey, long requestedTotal)
    boolean             isEnabled(OrganizationId, EntitlementKey)          // FLAG
    ListingEligibility  listingEligibility(OrganizationId)                 // may listings be shown?
  EntitlementDecision   { allowed, reason {NONE, LIMIT_REACHED, NO_SUBSCRIPTION, STATE_NOT_ALLOWED}, limit, requestedTotal }
  event SubscriptionStateChanged (already in the catalog)
```
Rules: the caller computes and passes `requestedTotal` (current count + the change) because usage lives in the owner module and entitlements flow downward only; subscription never calls back. Decisions are deterministic, side-effect free and cheap (cached per organization, evicted by `SubscriptionStateChanged`). `NO_SUBSCRIPTION` denies commerce-creating actions; read access and existing data are never blocked (ADR-19). A downgrade below current usage never deletes anything.

What exists in Phase 1 behind the contract (P-7): the `sub_plan`, `sub_plan_entitlement` and a minimal `sub_subscription` (state `ACTIVE`, assigned by a Super Admin, audited) tables and an admin endpoint to define a plan and assign it — **no prices, no payment, no trial, no renewal, no invoices** and no seeded plan values (OD-4 stays open; admins enter values at runtime). Everything else in subscription remains Phase 2.

## 12. Subsequent slices (unchanged order, dependencies made explicit)

| Slice | Content | Needs from earlier work |
|---|---|---|
| B | Organization, individual host, staff, invitations, five fixed roles, `VendorAccessApi` | Slice A principal/audit; entitlement contract for staff limit |
| C | Verification cases (subject-agnostic), admin queue and decisions, **no document upload**; evidence is structured data and text only | B; `VerificationDecided` consumed by organization |
| D | Property catalog, categories, amenities, approval states, listing-eligibility rule | B, C, entitlement contract (properties limit, listing eligibility) |
| E | Room types and individual units (inventory structure only, no calendar/availability) | D |
| F | Gallery/video media via `media` | OD-29 spike, OD-30 limits, L-18 for documents; blocked |

## 13. Decisions needed from you

| ID | Question | Recommendation |
|---|---|---|
| P-1 | Slice A is email-only (no phone auth) until an SMS provider is chosen (OD-20). | Accept. |
| P-2 | Add `ident_session` next to refresh tokens so access tokens can be revoked and sessions listed. | Accept. |
| P-3 | Verification codes and reset links must reach the notification module in plaintext once. Options: (a) outbound port implemented by `notification`, never persisted [recommended], (b) put the secret in the outbox event (persisted in the event table), (c) store reversible ciphertext. | (a). The catalog row for `VerificationCodeRequested` is corrected accordingly. |
| P-4 | Breached-password check: (a) bundled common-password denylist only [recommended for Slice A], (b) add an online k-anonymity lookup behind a port later (privacy and availability review first). | (a) now, (b) as a follow-up. |
| P-5 | First admin creation: a disabled-by-default bootstrap runner that runs only when no admin exists, reads email and a one-time password from secrets, forces TOTP enrolment, and is audited. | Accept. |
| P-6 | Audit hash chaining (OD-17): skip in Slice A (immutability via trigger and role privileges only). | Skip. |
| P-7 | Entitlements in Phase 1: contract plus minimal admin-assigned plans/subscription (no billing, no seeded values). The alternative is a configuration-only stub, which would be thrown away. | Accept the minimal tables. |
| P-8 | Admin MFA is TOTP only in Slice A (RFC 6238, own small implementation tested with the RFC vectors, no extra library); WebAuthn later. | Accept. |

## 14. Documents to update when implementation starts
`SECURITY_ARCHITECTURE.md` (sessions, mfa step, lockout values as configuration), `API_SPECIFICATION.md` (new endpoints), `DATABASE_DESIGN.md` (session, challenge, mfa tables), `EVENT_CATALOG.md` (§9.4), `DEVOPS.md` (secret names, DB role split), `STANDARDS_ENFORCEMENT.md` (route-catalog test becomes Active), `DECISIONS.md`/`OPEN_DECISIONS.md` (P-1..P-8 → ADRs), `CLAUDE.md` (current phase), `docs/README.md` (index of specs/plans).
