# Development Process and Work Plan

The single place that records how work proceeds, in what order, and what gates each step. It consolidates the audit of 2026-11-21 and the decisions taken afterwards. Phases of the product are in [ROADMAP.md](../product/ROADMAP.md); decisions in [DECISIONS.md](../DECISIONS.md); open questions in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md).

## 1. Rules of work

1. Read the relevant documents first; reference requirement ids.
2. Do not invent requirements. If the documents do not say it, it is not approved.
3. Test first. Run the relevant checks and report truthfully which were not run and why.
4. Keep documents and code in sync in the same change. If they disagree, stop and surface the conflict.
5. Every decision is labelled **Approved** (explicit user decision), **Proposed** (recommended default adopted so work can proceed; reversible until dependent code ships) or **Open**.
6. Do not commit or push unless asked. Review against [REVIEW.md](REVIEW.md) before declaring a task done.
7. Hard limit: a source file never exceeds **280 lines** (ADR-43).

## 2. Verified state at the start of this plan (2026-11-21)

- Implemented and tested in `backend/`: Phase 0 foundation and Phase 1 Slice A (identity, audit). Slice A is on branch `identity-audit-slice`, awaiting review and merge.
- Not started: every other module, the three web apps (only unmodified scaffolds exist in `web/`), the Flutter app (only the default scaffold exists in `client/`), `packages/`, any deployment.
- Findings that shape the plan: no tenancy implementation; admin roles are global; no `Money` value object; `platformconfig` is empty; no OpenAPI pipeline; no email delivery, so the backend cannot start in `STAGING` or `PRODUCTION`; two test files exceeded the new 280-line limit.

## 3. Approved decisions (2026-11-21)

| Decision | Record |
|---|---|
| Vendor commission: configurable, disabled by default, effective-dated, versioned, snapshotted, payer customer or vendor | ADR-35 |
| Configuration ownership split between `platformconfig` and business modules | ADR-36 |
| Cross-border bookings allowed; cards for cross-border; property country governs; no automatic conversion | ADR-37 |
| Gabon is the first launch market; architecture prepared for Mali, Côte d'Ivoire, Senegal; no hardcoded countries | ADR-38 |
| 280-line source file limit across the repository | ADR-43 |
| Orange-500 palette and shared identity across web and Flutter | ADR-44 |
| Railway as initial deployment platform; domains under `zellastay.org` | ADR-47 |
| Elasticsearch is not introduced; PostgreSQL remains the search solution; no microservices | ADR-6, ADR-1 |

Proposed defaults adopted for implementation (recommendations made, not individually confirmed): ADR-39 to ADR-42, ADR-45, ADR-46 and the detail parts of ADR-36, ADR-38, ADR-44, ADR-47. Each ADR states which part is which.

## 4. Work order

Each step ends with a report: what changed, checks run, checks not run, review findings. Steps marked **gate** may not be skipped.

| # | Step | Depends on | Notes |
|---|---|---|---|
| 1 | Documentation batch (this change): ADRs, multi-country, tenancy, contracts, Railway, runbooks, standards at 280 lines, roadmap | none | Done in this change |
| 2 | 280-line enforcement: repository script with its own tests, Gradle and CI wiring; split the two oversized test files | 1 | Tooling only |
| 3 | Review and merge Slice A | 2 | Needs the user to review and commit |
| 4 | Shared value objects: `Money`, `CurrencyCode`, `CountryCode`, `LocaleCode`, `DateRange`, with property-based tests (ADR-41) | 3 | |
| 5 | Country-scoped admin permissions and `country` on audit (ADR-40) | 4 | Changes Slice A code |
| 6 | `platformconfig`: registries, market lifecycle, readiness SPI, snapshot API, literal-ban test | 4, 5 | Option B permission port in `shared.security` (ADR-36) |
| 7 | **Gate:** RLS spike with the restricted database role; ADR-42 finalized | none (parallel to 4 to 6) | Nothing tenant-owned before it passes |
| 8 | Notification core with email, so identity works outside local development | OD-20 | Unblocks the first backend deploy |
| 9 | Backend deployment readiness: `PORT`, readiness path, pool, shutdown, production configuration, migration step, restricted role | 7 | |
| 10 | Web workspace and design tokens, then the shared UI package | ADR-44 decisions | See [WEB_ARCHITECTURE.md](../frontend/WEB_ARCHITECTURE.md) |
| 11 | OpenAPI pipeline (needs explicit approval for the backend change) | 3 | ADR-46 |
| 12 | Flutter foundation: structure, tokens, `design_system`, boundary checks | 10, 11 | No feature screens |
| 13 | CI/CD workflows and Railway provisioning from code | 9, 11, spikes | [RAILWAY_DEPLOYMENT.md](RAILWAY_DEPLOYMENT.md) |
| 14 | Phase 1 Slices B to F (organizations, verification, catalog, units, media), cross-tenant tests from the first endpoint | 7, 8 | |
| 15 | Phase 2 per [ROADMAP.md](../product/ROADMAP.md), with per-country gates | OD-18, L-1 per country | |

## 5. Definition of done (every step)

1. Failing test written first; it passes now.
2. `cd backend && ./gradlew build` (format, compile, all tests, architecture tests) passes for backend work; documentation check `python3 -I infra/tools/check-docs.py` passes; the file-size check passes.
3. Documents updated in the same change; new module dependencies in [MODULAR_ARCHITECTURE.md](../architecture/MODULAR_ARCHITECTURE.md) §4 and `package-info.java`.
4. Review against [REVIEW.md](REVIEW.md); findings and fixes listed.
5. Unexecuted checks listed with the reason. A check is never reported as passed unless it ran.

## 6. Backlog from the audit (not yet scheduled)

Done since the audit: nested enums and `AdminDtos`, the oversized and multi-use-case tests, `AdminAccountService`, `AdminMfaService`, the duplicated token parsing, `ApiExceptionHandler`, `IdentityConfiguration`, the audit-entry migration, the five-service review and the `AccessTokens` boundary, the split of `check-docs.py` into `infra/tools/docs_checks` with tests, and the shared `AudienceRoutes.of` factory. Still open:

- Password denylist upgrade (R-17); retention jobs for sessions and challenges; hash-chained audit (OD-17); README and roadmap status kept current.

## 7. Cross-repository enforcement

See [STANDARDS_ENFORCEMENT.md](STANDARDS_ENFORCEMENT.md). New in this plan: repository-wide 280-line check, country-literal ban, market-readiness test, contract drift and breaking-change gates, web and Flutter boundary checks, and security scanning in CI.

## 8. Progress log

| Date | Step | Result |
|---|---|---|
| 2026-11-22 | 1 Documentation batch | Done. ADR-35 to ADR-47, OD-31 to OD-39, L-19, R-19 to R-23; new documents MULTI_COUNTRY, TENANCY, API_CONTRACTS, RAILWAY_DEPLOYMENT, RUNBOOKS, this file; standards, roadmap, database, module graph (pricing and subscription gain `identity`), payment, business model, internationalization, frontend and DevOps documents updated. `check-docs.py`: 0 problems. |
| 2026-11-22 | 2 280-line enforcement | Done. `infra/tools/check-file-size.py` with `file-size.json` and 11 tests in `infra/tools/tests/`; wired into Gradle `check` and the new `quality-ci` workflow; `SourceFileSizeTest` retired. `LoginIT` (293) split into `LoginIT` and `RefreshSessionIT`; `ApiExceptionHandlerTest` (282) reduced by extracting `ErrorHandlingProbe`; shared `Auth.attempt` and `Auth.refresh` helpers. `./gradlew build`: 279 tests, 0 failures. Largest files now: `RegistrationIT` 279, `AdminAccountsIT` 277, `AdminAuthIT` 276 (close to the limit). |
| 2026-11-22 | SRP review: test infrastructure and tests | Done. `TestInfrastructure` split (containers, secrets, environment); probe fixtures one class per responsibility (`errorfixtures`, `outboxfixtures`); 6 multi-use-case integration tests split into 20 focused classes; repeated helpers consolidated into single-purpose support classes (`TestMailbox`, `AccountRecords`, `AuditEvents`, `RegistrationCalls`, `PasswordRecoveryCalls`, `SessionCalls`, `AdminAuthCalls`, `RepositoryTestData`); `ApiClientExtension` removes 19 duplicated setups. |
| 2026-11-22 | SRP review: mechanical production fixes | Done. `AdminDtos` split into 10 DTO files; `IssuedToken` and `TokenClaims` extracted from `AccessTokens`; nested public enums became `ChallengeCheckResult`, `RotationDecision`, `AdminBootstrapResult`. |
| 2026-11-22 | SRP review: `AdminAccountService` and `AdminMfaService` | Done. `AdminMfaService` (11 dependencies) became `AdminMfaEnrollmentService`, `AdminMfaVerificationService`, `MfaTicketReader`, `RecoveryCodes`, `SecondFactorVerifier`, `AdminSignInCompletion`; `AdminAccountService` (12) became `CreateAdminAccountService`, `AdminRoleService`, `AccountSuspensionService`, `AccountSessionRevocationService`, `AdminInvitations`; `ChallengeToken` is now the single parser and formatter of `<id>.<secret>` tokens (also used by password reset); `IdentityAuditTrail` writes the audit entries of these classes; `AdminAccountController` became four thin controllers on the same routes. Largest new class: 107 lines, at most 8 dependencies. `./gradlew build`: 291 tests, 0 failures. |
| 2026-11-22 | SRP review: `ApiExceptionHandler` and `IdentityConfiguration` | Done. `ApiExceptionHandler` (216 lines) keeps exception routing and logging (155 lines); validation field-error mapping moved to `ValidationProblems`, and the status-to-code mapping, which `ApiErrorController` was calling on the advice, moved to `StatusErrorCodes`. `IdentityConfiguration` became `CredentialConfiguration`, `TokenConfiguration`, `MfaConfiguration`, `ChallengeConfiguration` and `DevelopmentDeliveryConfiguration`; the denylist file loading moved to `PasswordDenylist`. `./gradlew build`: 298 tests, 0 failures. |
| 2026-11-22 | SRP review: audit-entry migration and five-service review | Done. All 14 hand-built audit entries in `identity.application` now go through `IdentityAuditTrail`, the only class that touches the audit recorder. Reviewed `ResetPasswordService` (11 to 9 dependencies: the challenge, account, kind and eligibility lookup was written twice, now `ResetTokenReader`; applying the reset is `PasswordResetCompletion`; the "may reset" rule moved from a static method on another service into `Account.canResetPassword()`), `AdminLoginService` (8 to 5: reuses `ChallengeIssuer.issueMfaTicket`), `ChangePasswordService` (the swallowed concurrent-update exception is now logged). `RefreshSessionService` (10), `RegisterAccountService` (9) and `ChangePasswordService` (9) were judged cohesive single use cases and kept whole. `./gradlew build`: 300 tests, 0 failures. |
| 2026-11-22 | SRP review: `AccessTokens` | Done. The bearer filter no longer parses tokens or checks sessions; that is `BearerTokenAuthenticator` in `identity.application` (signature, expiry, audience, usable session, principal assembly). The filter dropped from 5 to 3 dependencies and `web` no longer imports anything from `identity.domain`; `SessionGuard` became package-private. New ArchUnit rule "web uses application services, never domain ports" with a violating fixture. A stricter rule banning every `web` to `domain` reference was tried and rejected: it flags 18 legitimate uses in `audit.web` (mapping returned value objects) and would force duplicate view types. `./gradlew build`: 304 tests, 0 failures. |
| 2026-11-22 | SRP review: ADR-48 and structure rules | Done. ADR-48 records the ten rules; ArchUnit `StructureRules` enforces the constructor limit (10) and non-public, one-level nesting, proven by fixtures; `SessionManagementService.SessionView`, `RateLimitCounter.Hit` and `AuditPartitionMaintainer.Report` became top-level `SessionSummary`, `RateLimitHit` and `PartitionMaintenanceReport`; checklist rows added to REVIEW.md. |
| 2026-11-22 | Package-level cycle rule | Done. `packagesAreFreeOfCyclesWithinModules` checks slices of `<module>.<package>`; production code already passed; proven by a cyclic fixture and a clean fixture. |
| | 3 Review and merge Slice A | Not done: needs the user's review and commit decision. |
| | 4 onward | Not started. Steps 4 to 6 depend on confirming the Proposed ADRs (ADR-39 to ADR-42). |



Not run in step 2: `actionlint` and `gitleaks` (not installed here), the `quality-ci` workflow itself (needs GitHub), Flutter and web checks (no code yet).
