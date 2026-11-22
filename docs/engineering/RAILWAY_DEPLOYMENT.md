# Railway Deployment and Production Infrastructure

Railway is the initial deployment platform (ADR-47). The backend stays a modular monolith; no microservice is introduced for deployment reasons and no separate worker exists until an operational need is demonstrated.

**Status:** design only. Nothing is deployed and no Railway project exists. Facts about Railway come from its documentation as read on 2026-11-21; items marked **unverified** must be confirmed in a throwaway spike before the design relies on them (§13). Decisions awaiting an answer are in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md) (OD-36 to OD-38).

## 1. Services

Per environment: `backend` (Java 21, Spring Boot 4), `website`, `admin`, `vendor` (Next.js), `postgres`, `redis`.

```
Internet --TLS--> website / vendor / admin (Next.js BFF) --private network--> backend --> postgres, redis
Flutter app --TLS--> api.zellastay.org --> backend
Providers (payment, Cloudinary) --webhooks--> api.zellastay.org
```

- Databases are private. Public access (TCP proxy) stays off; administration uses the Railway CLI tunnel.
- Web apps call the backend over the private network. Private names resolve only inside one environment.
- Open option: `/api/v1/admin/**` is not routed publicly on the API domain (edge rule or host check), so only the admin app reaches it privately.

## 2. Environments and isolation

Staging and production are fully isolated: backend, PostgreSQL, Redis, variables and secrets, deployment configuration, provider credentials and Cloudinary configuration. Staging never receives production credentials or customer data, and restore drills never load raw production data into staging.

**Recommended topology (Proposed, OD-36): two Railway projects**, `zellastay-staging` and `zellastay-production`, with different members and project-scoped tokens. Reason: Railway environments inside one project share project membership and environment-level access control is an Enterprise feature, so a separate project is the only hard boundary on the Pro plan. Production secrets are sealed (write-only; sealed variables are not copied when duplicating).

Plan: **Pro**. Scheduled volume backups and point-in-time recovery require Pro, two custom domains per service is the Hobby cap, and Pro allows enforced two-factor authentication.

## 3. Domains and DNS

| Hostname | Service | Notes |
|---|---|---|
| `zellastay.org` | website | Apex needs CNAME flattening or an ALIAS at the DNS provider |
| `www.zellastay.org` | redirect to `zellastay.org` | Railway documents no native redirect; use the DNS or edge provider's redirect rule |
| `admin.zellastay.org` | admin | |
| `vendor.zellastay.org` | vendor | |
| `api.zellastay.org` | backend | Flutter app and provider webhooks |

- For each custom domain Railway displays the exact CNAME target and a TXT verification record. **Both are required**. Use the values Railway shows; never invent or commit them, and never hardcode Railway-generated hostnames.
- TLS: Railway issues and renews Let's Encrypt certificates automatically; external certificates are unsupported.
- If the DNS provider proxies traffic, Railway's docs require SSL mode Full (not Full Strict); the wildcard verification record must not be proxied.
- Staging uses separate hostnames (proposal, OD-37): flat names such as `staging.zellastay.org`, `api-staging.zellastay.org`, `admin-staging.zellastay.org`, `vendor-staging.zellastay.org`, to avoid nested-subdomain certificate constraints.
- **Same-site warning:** all hostnames share the registrable domain, so `SameSite` does not isolate admin, vendor and website. Use host-only cookies (no `Domain` attribute), the `__Host-` prefix, CSRF tokens and `Origin` checks. Whether admin moves to its own registrable domain is open (OD-37).
- Domain registrar and DNS provider for `zellastay.org` are not yet recorded (OD-37).

## 4. CI/CD architecture

GitHub Actions validates and, for deploys, drives Railway.

| Trigger | Result |
|---|---|
| Pull request | Validation only: format, lint, type check, tests, architecture tests, 280-line check, dependency, secret and image scans |
| Merge to `main` | Build one image per changed service, scan it, push to the container registry tagged with the commit SHA, deploy to staging |
| Production | Manual approval (GitHub Environment with required reviewers), then the **same image digest** is deployed to production |

- This keeps the trunk-based flow of [CI_CD.md](CI_CD.md) §5 and "build once, promote the same artifact". A long-lived `develop` branch is not adopted unless decided otherwise (OD-36).
- Deploys use project-scoped Railway tokens stored as GitHub Environment secrets. Fork pull requests run without secrets.
- Unrelated changes do not deploy: each service has a path filter in CI (for example `backend/**`, `web/admin/**`, `packages/**` for the web apps). If Railway's own GitHub deploys are used instead, equivalent watch patterns are required and resolve from the repository root (for example `/backend/**`) even when a root directory is set.
- Required checks: Java compile and tests; backend architecture tests; Flutter format, analyze and tests; Next.js lint, type check and tests; shared package tests; 280-line check; dependency vulnerability scan; secret scan; container image scan; workflow security checks. CI fails when any mandatory check fails.

## 5. Configuration as code

Railway's `railway.toml` / `railway.json` are **deprecated**: new services cannot use them and existing files stop being read on **2026-12-01**. The replacement is Infrastructure as Code in `.railway/railway.ts` (TypeScript is generally available). Confirmed: it covers services, databases, volumes, custom domains, variables, replicas and a healthcheck path. **Unverified:** root directory, Dockerfile path, watch paths, pre-deploy command, restart policy and draining time. Do not add `railway.toml`. Spike first (§13).

Infrastructure files live under `infra/railway/` with documentation; they contain no secrets or production credentials.

## 6. Backend deployment

| Topic | Requirement | State |
|---|---|---|
| Image | Existing multi-stage Dockerfile, non-root | Exists |
| Port | Bind to Railway's injected `PORT` (the healthcheck uses the same value) | Not implemented: the app reads `ZS_SERVER_PORT` |
| Health | Railway checks only the `PORT` listener, only at deploy time. Expose the readiness group on the main port through Spring's health-group additional path (for example `/readyz`) and keep metrics on the private management port | Not implemented, unverified on Spring Boot 4.1 |
| Host header | Railway healthcheck requests come from `healthcheck.railway.app`; the backend has no host allow-list | Nothing to do |
| Shutdown | `server.shutdown: graceful` exists (30 s default phase timeout); the platform drain time must exceed it | Platform setting unverified |
| Pool | Explicit Hikari limits; replicas × pool must stay below PostgreSQL `max_connections` (unverified) | Not implemented |
| Secrets | Railway variables (sealed in production). Signing key (multi-line PEM, handling unverified), MFA key, pepper. Docs previously preferred mounted files, which Railway cannot provide | Code already accepts variables |
| Profiles | A production configuration with no development defaults or debug settings | Not implemented |
| Migrations | Dedicated pre-deploy migration step with the owner role; the running service uses the restricted role and Flyway disabled | Not implemented; the app currently runs Flyway at startup with one role |
| Startup guard | In `STAGING` and `PRODUCTION` the backend refuses to start without a real `VerificationDelivery` (notification module). This is a safety check and must not be weakened | Blocks the first staging deploy |
| Logs | Structured ECS JSON already configured | Exists |

Destructive migrations are never applied automatically. Migrations are forward-only with expand/contract and backward compatible with the previous release; recovery is forward-fix or restore (§9).

## 7. Next.js services

Three standalone Docker images (`output: 'standalone'`). API and public configuration are read at runtime, not baked at build, so the same image runs in staging and production. Production browser source maps are off (upload privately to the error tracker). Nonce-based CSP (verify against Cache Components), secure headers, no tokens in `localStorage`, session cookies set by the BFF with `HttpOnly`, `Secure` and host-only attributes, strict CORS allow-list on the backend for any direct browser call.

## 8. Flutter environment configuration

Flavors `dev`, `staging`, `prod` through `--dart-define-from-file`. Only non-secret values (for example `API_BASE_URL`). Production uses `https://api.zellastay.org`; staging uses the staging API hostname once approved. HTTPS only, tokens in keychain/keystore, no provider secrets in the bundle.

## 9. PostgreSQL

- Three backup layers (Railway's own recommendation): point-in-time recovery (about four weeks of archive, restores to a new sibling service, cutover is manual, starts only after it is enabled); scheduled volume backups (retention figures differ between Railway pages, verify in the dashboard); and an independent daily `pg_dump` to an external bucket in a different account, encrypted. Volume backups are deleted with the volume, so they are not disaster recovery by themselves.
- **Image risk (R-19):** Railway PITR uses its pgBackRest-based image. PostGIS (ADR-6) is a separate template, and custom images such as PostGIS are documented as not HA-compatible. Whether PITR works with PostGIS, and whether `btree_gist`, `pg_trgm` and `pgcrypto` are available, is unverified. Spike before choosing the image; the `earthdistance` fallback of ADR-6 remains available.
- Targets (Proposed, OD-38): RPO 15 minutes, RTO 4 hours at launch.
- Restore drills: before go-live, quarterly and after major schema changes. Drills restore into a disposable isolated service that is destroyed afterwards. A drill is not complete until the restored database passes the application's invariant checks.
- Roles: owner (migrations) and restricted application role; RLS tests use the restricted role ([TENANCY.md](../architecture/TENANCY.md)). Whether Railway's default user is a superuser is unverified.
- PgBouncer is not used at launch. If added, transaction mode forbids advisory locks and session-level `SET`; tenant context is transaction-local, so it would be compatible.
- Monitoring: connection count, slow queries, locks, replication or archive lag, disk usage.

## 10. Redis

Private network only, password required, explicit `maxmemory` and eviction policy, separate instance per environment. Redis is never the source of truth for financial or booking records: it holds rate-limit counters and a short session-status cache, and the code degrades safely when it is down. Template defaults for version, eviction and persistence are undocumented; confirm or run a configured instance.

## 11. Media

Cloudinary only, configured per environment with separate credentials and folder prefixes. The API secret exists only in the backend. Nothing is stored on the ephemeral application filesystem. See [MEDIA_ARCHITECTURE.md](../architecture/MEDIA_ARCHITECTURE.md).

## 12. Monitoring and alerting

Railway's healthcheck runs only at deploy time and is not continuous monitoring. Required: an external uptime monitor on the readiness path and key pages; an error tracker; application metrics (Micrometer on the private management port, scraped by a collector or agent); database and Redis metrics; deployment and resource-usage alerts; request correlation ids in every log line. Alert rules and invariants: [OBSERVABILITY.md](OBSERVABILITY.md). Tool choice and budget are open.

## 13. Unverified Railway items (spike before relying on them)

`.railway/railway.ts` coverage (root directory, Dockerfile path, watch patterns, pre-deploy command, restart policy, draining time); a CI gating option on Railway-side deploys; private-registry image deploys; private-network IPv6 or dual-stack behavior (the JVM may need a flag); Postgres default user, roles, extensions and `max_connections`; Redis defaults; available regions (latency to Gabon and data residency L-6); the proxy's `X-Forwarded-For` behavior (R-18); per-environment custom domains; backup retention; audit-log availability by plan.

## 14. Cost model (estimate, not a measurement)

Railway unit prices read on 2026-11-21: memory $10/GB-month, CPU $20/vCPU-month, volumes $0.15/GB-month, egress $0.05/GB, builds free; Pro $20/month including $20 of usage.

| Item | Production | Staging |
|---|---|---|
| Backend | about $16 | about $10 |
| PostgreSQL with volume | about $16 | about $8 |
| Redis | about $3.5 | about $2 |
| Three Next.js apps | about $15 | about $10 |
| Egress and backup storage | about $6 | about $1 |
| Subtotal | about $57 | about $32 |

About $90 per month in total; realistic range $70 to $150, driven mainly by JVM memory. Not included: Cloudinary, error tracking, uptime monitoring, the offsite backup bucket, domain and DNS. Scaling: backend replicas scale memory cost linearly; chat needs the Redis bridge (ADR-14) before more than one replica; high-availability PostgreSQL multiplies database cost several times.

## 15. Production readiness checklist

- [ ] RLS spike passed and ADR-42 accepted; tenant tables use the restricted role
- [ ] Notification module and provider live, so the backend starts in `PRODUCTION`
- [ ] Restricted database role and migration pre-deploy step in use
- [ ] PITR enabled, offsite dump running, restore drill passed and recorded
- [ ] `PORT` binding, readiness path, pool and shutdown configuration verified in staging
- [ ] Client-IP handling verified behind Railway's proxy (R-18)
- [ ] DNS, TLS and redirect verified for every hostname
- [ ] Secrets sealed, rotated procedure rehearsed, no secrets in Git or images
- [ ] CI scans (dependency, secret, image) mandatory and green
- [ ] Error tracking, uptime monitoring and alerts live with an on-call owner
- [ ] Runbooks written and rehearsed ([RUNBOOKS.md](RUNBOOKS.md))
- [ ] Legal items L-1, L-5 to L-7, L-12, L-18 closed for the launch country
- [ ] Penetration test and threat model for the exposed surface
- [ ] Cost alert and budget set
