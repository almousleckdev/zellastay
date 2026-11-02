# DevOps

## 1. Principles

12-factor configuration, immutable artifacts, infrastructure as code, least privilege, reproducible environments, cloud-provider portability. Cloud provider, region and hosting model are undecided `[OD-15]` (may be constrained by data residency `[LEGAL]` and latency to Gabon).

## 2. Repository layout (target)

```
zellastay/
  backend/                    Spring Boot modular monolith (single deployable)
  apps/
    customer-mobile/          Flutter
    public-web/               Next.js
    vendor-dashboard/         Next.js
    admin-dashboard/          Next.js
  packages/                   Shared web packages (ui, api-client, design-tokens, config, i18n-utils)
  infra/                      IaC (Terraform or equivalent), Helm/Kustomize or compose manifests, runbooks
  docs/
  .github/workflows/
  CLAUDE.md  README.md
```

Monorepo, path-filtered CI. Decision recorded in [DECISIONS.md](../DECISIONS.md) (ADR-3). `backend/`, `infra/` and `.github/` exist (Phase 0); `apps/` and `packages/` are created when each frontend is scaffolded.

## 3. Environments

| Env | Purpose | Data |
|---|---|---|
| local | Developer machines via Docker Compose (PostgreSQL, Redis, mail catcher; Kafka only when introduced; media is a dedicated Cloudinary development environment or a fake `MediaStorage` in tests, no emulator) | Synthetic via test tooling only |
| ci | Ephemeral per pipeline run | Synthetic |
| preview | Per-PR web previews (optional) | Synthetic |
| staging | Production-like; provider sandboxes | Synthetic/anonymized; never raw production dumps |
| production | Live | Real |

Configuration parity: same artifacts across environments, differing only by injected configuration.

## 4. Containers

- Multi-stage Dockerfiles; distroless/minimal runtime images; run as non-root; read-only root filesystem where possible; no secrets baked in; pinned base image digests; healthcheck endpoints (`/actuator/health/liveness|readiness`); JVM container-aware memory settings; graceful shutdown (drain WebSockets and in-flight work).
- Images signed and scanned; SBOM generated.
- Registry with retention and promotion by tag (immutable `sha-<commit>` tags; environments reference digests).

## 5. Deployment

- Rolling or blue/green deployments with automatic rollback on failed health/SLO checks.
- Database migrations run as a dedicated pre-deploy job; backward-compatible with the previous app version (expand/contract).
- Feature flags (database-backed config in `platformconfig`) decouple deploy from release.
- Web apps: containerized standalone Next.js or managed platform `[OD-15]` behind CDN; mobile: store releases with staged rollout.
- Zero-downtime for WebSockets: clients reconnect with backoff; connection draining.

## 6. Secrets and configuration

Secret manager of chosen cloud; injected at runtime as env vars or mounted files; rotation procedures documented (JWT signing keys with `kid` overlap, DB credentials, provider keys, webhook secrets); separate credentials per environment; developers never use production secrets locally; CI uses short-lived OIDC credentials, not long-lived keys.

## 7. Data operations

- PostgreSQL: managed service with PITR, automated backups, encryption, restore drills (quarterly), RPO/RTO from `[OD-14]`, monitored replication lag, connection pooling (HikariCP in app; PgBouncer if needed), autovacuum tuning for hot tables (`inv_availability`), partition maintenance jobs for audit/outbox archive.
- Redis: managed, persistence not required for correctness; eviction policy and memory alarms.
- Media: Cloudinary environments per stage with separate credentials; folder prefix per environment; backups disabled or purge procedure defined for private documents; delete with CDN invalidation ([MEDIA_ARCHITECTURE.md](../architecture/MEDIA_ARCHITECTURE.md)).
- Retention jobs (chat, notification content, audit archival) run as scheduled, audited jobs.

## 8. Networking and edge

CDN + WAF + DDoS protection in front of web apps and API; rate limiting at edge and app; admin dashboard restricted network access; private networking between app and data stores; egress allow-list for provider calls where feasible; separate domains for app, API, media (cookie isolation), admin.

## 9. Infrastructure as code

Everything reproducible from `infra/`; changes via PR with plan output reviewed; drift detection; environment modules parameterized by country/region for future multi-region expansion.

## 10. Operations

On-call and incident process, severity matrix, runbooks (payment provider outage, stuck outbox, notification backlog, reconciliation exception surge, DB failover, credential compromise), status page, post-incident reviews. Cost monitoring and budgets per environment.

## 11. Local developer experience

One command bootstraps dependencies (`docker compose up`), another runs checks; documented in README per app once created. `.env.example` files list variables without values; real `.env` ignored by git.

## 12. Local environment and configuration contract (implemented in Phase 0)

Commands (from the repository root):

| Task | Command |
|---|---|
| Create local settings | `cp infra/docker/.env.example infra/docker/.env`, then set the empty password values (the file is git-ignored) |
| Start PostgreSQL and Redis | `docker compose -f infra/docker/compose.yaml --env-file infra/docker/.env up -d` |
| Run the backend on the host | `infra/scripts/run-backend-local.sh` |
| Run the backend in a container too | `docker compose -f infra/docker/compose.yaml --env-file infra/docker/.env --profile app up -d --build` |
| Build and test | `cd backend && ./gradlew build` (needs Docker running for Testcontainers) |
| Format | `cd backend && ./gradlew spotlessApply` |
| Check docs | `python3 -I infra/tools/check-docs.py` |

Host ports are configurable (`ZS_DB_HOST_PORT`, `ZS_REDIS_HOST_PORT`, `ZS_SERVER_PORT`, `ZS_MANAGEMENT_PORT`) so the stack can coexist with other local services.

Environment variables read by the backend (no defaults for anything secret or environment-specific; startup fails fast naming the missing key):

| Variable | Required | Purpose |
|---|---|---|
| `ZS_ENVIRONMENT` | yes | `LOCAL`, `CI`, `STAGING` or `PRODUCTION`; metric tag and typed property `zellastay.environment` |
| `ZS_DB_URL`, `ZS_DB_USER`, `ZS_DB_PASSWORD` | yes | PostgreSQL |
| `ZS_REDIS_HOST`, `ZS_REDIS_PASSWORD` | host yes; password optional | Redis (`ZS_REDIS_PORT` defaults to 6379) |
| `ZS_SERVER_PORT`, `ZS_MANAGEMENT_PORT` | no | Defaults 8080 / 8081; management port serves `/actuator/health/{liveness,readiness}` and `/actuator/prometheus` only and must not be publicly routed |
| `ZS_SECRETS_DIR` | no | Directory of mounted secret files (default `/run/secrets/`). A file named like a variable (for example `ZS_DB_PASSWORD`) supplies that variable, via Spring `configtree:` |

Identity variables (read since Slice A task A1): `ZS_IDENTITY_TOKEN_SIGNING_KEY` (secret; PKCS#8 PEM of an EC P-256 key, supply as a file under `ZS_SECRETS_DIR`), `ZS_IDENTITY_TOKEN_SIGNING_KEY_ID` (non-secret label of that key), `ZS_IDENTITY_CHALLENGE_PEPPER` (secret; keys the verification-code hash). All three are required; startup fails naming the variable. Lifetimes and thresholds are typed properties under `zellastay.identity.*` in `application.yaml` (initial baselines pending security review, overridable per environment). Locally, `infra/scripts/generate-local-secrets.sh` creates the two secret files in the git-ignored `infra/docker/secrets/` directory and `run-backend-local.sh` points `ZS_SECRETS_DIR` at it; the Compose `app` profile mounts it read-only at `/run/secrets`.

Further identity variables (Slice A): `ZS_IDENTITY_MFA_ENCRYPTION_KEY` (secret; 32 random bytes, Base64; encrypts authenticator seeds), `ZS_IDENTITY_MFA_ENCRYPTION_KEY_ID` (label). Optional first-admin bootstrap: set `ZS_IDENTITY_BOOTSTRAP_ENABLED=true`, `ZS_IDENTITY_BOOTSTRAP_ADMIN_EMAIL` and the secret `ZS_IDENTITY_BOOTSTRAP_ADMIN_PASSWORD` for one start-up; it creates a Super Admin only when no administrator exists (who must then enrol an authenticator), is audited, and should be switched off afterwards. Rate limits are `zellastay.rate-limit.{auth,api}.{requests,window}`; the client address comes from `X-Forwarded-For`, so the backend must be reachable only through a proxy that overwrites that header (R-18).

Audit: `zellastay.audit.partitions-ahead` and `zellastay.audit.maintenance-cron` configure the partition job. In deployed environments run Flyway migrations with an owner role and the application with a separate role granted only `SELECT` and `INSERT` on `audit_event` (the trigger is the safeguard that also works with a single role, as in local development and CI).

Startup also fails before any bean is created, naming every missing required setting (`ZS_ENVIRONMENT`, `ZS_DB_URL`, `ZS_DB_USER`, `ZS_DB_PASSWORD`, `ZS_REDIS_HOST`).

Planned variables (added when the media module is implemented, not read yet): `ZS_CLOUDINARY_CLOUD_NAME` (public), `ZS_CLOUDINARY_API_KEY`, `ZS_CLOUDINARY_API_SECRET` (secret; backend only, preferably via `ZS_SECRETS_DIR`).

Secret management rules: values never appear in the repository, images, compose files or logs; locally they come from the git-ignored `infra/docker/.env`; in deployed environments from the cloud secret manager, injected either as environment variables or as files under `ZS_SECRETS_DIR` (preferred, because files are not visible through process listings). Tests generate throwaway credentials for ephemeral containers at runtime.
