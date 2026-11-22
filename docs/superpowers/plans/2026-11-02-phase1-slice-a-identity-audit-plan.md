# Phase 1, Slice A — Identity and Audit: Implementation Plan (for review)

Status: **approved; A0 to A9 and A11 done (A10, the entitlement contract, moves to the start of Slice B with its first consumer).** Design: [the Slice A design](../specs/2026-11-02-phase1-slice-a-identity-audit-design.md). Decisions P-1..P-8 in that document are assumed approved as recommended unless changed.

## Working rules for every task
- Test first: write the failing test, watch it fail for the right reason, implement, run.
- One module per task where possible; files ≤ 300 lines, one responsibility; no framework types in `domain`.
- Every new exception has an `ErrorCode` with French and English text (`ErrorCatalogTest` must stay green).
- Docs and code change together (module graph, API, DB, events, config contract).
- Definition of done per task: `./gradlew build` (format, `-Xlint:all -Werror`, all tests, architecture and error tests, JaCoCo), `python3 -I infra/tools/check-docs.py`, and, when workflows or secrets are touched, actionlint and gitleaks. Checks that cannot run are reported, never assumed.
- Nothing is committed or pushed without explicit approval; I propose one commit per task group (A0–A9) for review.

## Task list

### A0 — Contracts and decisions in the documents (no code)
Record P-1..P-8 as ADRs/ODs, update the API, DB, security, event, DevOps and enforcement documents listed in design §14.
*Acceptance:* docs check clean; module graph unchanged; every new endpoint, table, event, error code and configuration name appears in exactly one authoritative document.

### A1 — Security dependencies and configuration skeleton
Add to the version catalog: `spring-security-crypto` and `spring-security-oauth2-jose` (Nimbus JOSE) from the Boot BOM, BouncyCastle (Argon2). The Spring Security web starter, resource server and `spring-security-test` arrive with A8 so endpoint behavior is unchanged until the filter chains exist. Typed `@ConfigurationProperties` for tokens, lockout, rate limits, challenge TTLs (no defaults for secrets; fail fast when missing outside `LOCAL`/`CI` test profile).
*Tests first:* configuration binding/validation test; missing-secret startup failure test; `ConfigurationSourcesTest` extended (secret files supply keys).
*Acceptance:* application still boots under all profiles; no secret values in repo (gitleaks).

### A2 — Audit module
Migration `audit_event` (monthly partitions, default partition, immutability trigger), `AuditRecorder` (privileged joins transaction; best-effort uses a new transaction), allow-list validation of `details`, partition-maintenance job, the query service with cursor pagination. **Change:** the `GET /admin/audit-events` endpoint and its `audit.read` allowed/denied tests move to A9, because exposing an admin endpoint before the security filter chains (A8) and admin roles (A9) exist would be unauthenticated.
*Tests first:* recorder persists all fields incl. `requestId`; rollback of the caller removes a privileged entry while a best-effort entry survives; `UPDATE`/`DELETE`/`TRUNCATE` are rejected; a forbidden detail key is rejected; partition job creates future partitions and is idempotent; query filters and cursor pagination; viewer denied without authority (allowed/denied cases).
*Acceptance:* audit module depends only on `shared`; ArchUnit and Modulith green; no secret-like value can be recorded.

### A3 — Identity domain (pure)
`Account` aggregate and lifecycle, `Email` (normalization, masking), `PasswordPolicy`, `VerificationChallenge` (attempts, expiry, cooldown), `Session`/`RefreshTokenFamily` (rotation, reuse), `AdminRole`; repository interfaces.
*Tests first (pure unit, no Spring):* every legal and illegal state transition; password policy table (length 9/10/128/129, NFKC equivalents, denylist, equals-email-local-part); challenge expiry, attempts, burn; refresh rotation, reuse detection, concurrency-grace window; lockout progression with injected `Clock`.
*Acceptance:* domain has no Spring/JDBC imports (ArchUnit); all time via `Clock`.

### A4 — Identity persistence
Migrations for the `ident_*` tables (design §3), unique `(kind, email_normalized)`, partial indexes, `version` column for optimistic locking; JDBC repositories in `infrastructure`; Argon2id encoder adapter with rehash-on-login.
*Tests first (Testcontainers PostgreSQL):* repository round-trips; uniqueness per kind (same email allowed across kinds, not within); concurrent registration of the same email yields exactly one account; optimistic-lock conflict maps to `data.concurrent-modification`; hash parameters upgrade path.
*Acceptance:* no cross-module table access; migrations run on an empty and on an existing Phase 0 database.

### A5 — Registration and email verification
Use cases `RegisterAccount` (customer, vendor user), `VerifyEmail`, `ResendVerification`; `VerificationDelivery` port with the development/test sink; events `AccountRegistered`, `VerificationCodeRequested` (no secret) via the outbox; endpoints `/auth/register`, `/auth/verify`, `/auth/verify/resend`, `/vendor/auth/register`.
*Tests first:* registration returns identical responses for new and existing emails (body, status, headers; timing within a tolerance); verification success activates, wrong code increments attempts, 5th failure burns the challenge, expired code rejected, resend cooldown and hourly cap enforced; the plaintext code is never in logs, events or the database (assert on all three); validation errors use the problem+json shape without rejected values; French/English messages; `PRODUCTION` profile refuses to start with only the development sink.
*Acceptance:* FR-ID-1 (email), FR-ID-4 partially; an audit entry per registration/verification.

### A6 — Login, tokens, sessions
`Login`, `RefreshSession`, `Logout`, `LogoutAll`, session list/revoke; JWT issuer/validator with key ring; session cache with database fallback; endpoints `/auth/login`, `/auth/refresh`, `/auth/logout`, `/me/sessions`.
*Tests first:* uniform failure for unknown/wrong/unverified/suspended-before-password-check; lockout and recovery after expiry; access token claims and audience; expired/forged/`alg=none`/wrong-key/wrong-audience tokens rejected; refresh rotation; reuse revokes the family and writes the audit event; concurrency-grace retry succeeds once; idle and absolute expiry; revoked session rejected within the cache TTL bound and immediately after eviction; Redis down falls back to the database; key rotation (token signed with the old key still valid until retired).
*Acceptance:* no endpoint ever returns or logs a token hash; token and session behaviour matches design §5.

### A7 — Password change and recovery
`ChangePassword`, `RequestPasswordReset`, `ResetPassword`; endpoints `/auth/password/forgot`, `/auth/password/reset`, `/me/password`.
*Tests first:* `forgot` identical for known/unknown emails; reset token single-use, expiring, hashed at rest; successful reset revokes all sessions and notifies the owner; policy applied; change requires the current password and revokes other sessions; both audited; rate-limited per account and per IP.
*Acceptance:* no information leak; an old password stops working immediately.

### A8 — Security chains, rate limiting, deny-by-default
`SecurityFilterChain` per audience with audience checks, 401/403 entry points delegating to `ProblemFactory`, security headers, CORS allow-list, `shared.web` Redis rate limiter with in-memory fallback, route-catalog test.
*Tests first:* a customer token on `/vendor/**` and `/admin/**` (and each other pairing) is rejected; anonymous access limited to `/public/**` and auth endpoints; the route-catalog test fails when a handler has no explicit rule (proven with a fixture controller); 429 with `Retry-After`; limiter behaviour when Redis is unavailable; error bodies for 401/403 equal the documented contract.
*Acceptance:* the "authorization test for every endpoint" row in STANDARDS_ENFORCEMENT becomes Active for the covered routes.

### A9 — Admin accounts, MFA, roles, account administration
Bootstrap runner (P-5), admin login in two steps, TOTP enrolment/verification (RFC 6238 vectors), recovery codes, encrypted TOTP secret, role → permission data, `AdminAccessApi`, admin endpoints to create admins, suspend/reinstate accounts, revoke sessions, and the account-deactivation self-service.
*Tests first:* bootstrap runs only when enabled and no admin exists, never twice, and audits; password-only token cannot call admin endpoints; TOTP accepts the current ±1 step, rejects replay of a used step and wrong codes (with attempt limits); recovery code single-use; role matrix allowed/denied tests for each permission; suspension revokes sessions immediately and emits `AccountSuspended`; reinstate emits `AccountReinstated`; every mutation writes a fail-closed audit entry; masked-by-default contact data.
*Acceptance:* FR-ID-4 and the admin rules in SECURITY §1, §3 satisfied; no admin self-registration path exists (route catalog proves it).

### A10 — Entitlement contract (interfaces and tests only, if approved with Slice B)
`subscription.api` types from design §11 with a fake implementation for consumers' tests; the minimal tables and admin endpoints (P-7) land at the start of Slice B, where `MAX_STAFF` is first needed.
*Acceptance:* `subscription` module graph unchanged; contract tests document each decision reason.

### A11 — Closing verification for the slice
Full uncached build, docs check, actionlint, gitleaks over the would-be tree, a security-focused review against `docs/engineering/REVIEW.md`, mutation checks that deliberately break the guards (remove a rule from a chain, make a code plaintext, drop the audience check, skip a bundle key) and prove the build fails, a list of unexecuted checks. Update `CLAUDE.md` current-phase text and the index.
*Acceptance:* all green; slice report with commit proposals; nothing pushed.

## Dependencies and order
A0 → A1 → A2 and A3 (parallel) → A4 → A5 → A6 → A7 → A8 → A9 → A10 → A11. A8 can start its route-catalog fixture earlier; it is last in the chain because every previous endpoint must be registered in it.

## Risks
| Risk | Handling |
|---|---|
| Spring Boot 4 / Spring Security 7 starter names and APIs differ from older guides | First action of A1 is a compile-and-boot spike on the pinned versions; findings go into the ADR. |
| Argon2 cost vs CI speed | Test profile uses minimum parameters through typed configuration; one production-parameter test asserts the configured cost. |
| Timing-equality tests are flaky | Compare distributions on a repeated sample with a generous tolerance; the property that matters (the dummy verification runs) is asserted by a spy. |
| Email delivery absent until the notification slice | Development sink only; production profile refuses to start without a real `VerificationDelivery`; a later notification slice implements it (needs OD-20). |
| Partitioned audit table complexity | Partition job tested; default partition plus alert; sized for monthly volume. |

## Acceptance of the plan itself
You approve: the scope and non-goals (design §1), P-1..P-8 (or give changes), the task order, and the commit-per-task-group proposal. I then start with A0 and A1 only and report before continuing.
