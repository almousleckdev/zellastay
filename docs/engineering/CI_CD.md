# CI/CD (GitHub Actions)

Deployment to Railway (build once, promote the same image digest, production behind an approval gate) is specified in [RAILWAY_DEPLOYMENT.md](RAILWAY_DEPLOYMENT.md) §4. Implemented in Phase 0: `backend-ci` (format check, compile with `-Xlint:all -Werror`, unit/architecture/integration tests on Testcontainers, JaCoCo report, container image build), `docs-ci` (`infra/tools/check-docs.py`: links, decision references, AI-attribution scan) and Dependabot (Gradle, GitHub Actions, Docker). All third-party actions are pinned by commit SHA. The remaining pipelines below are created with their components.

## 1. Pipelines

| Workflow | Trigger | Paths | Jobs |
|---|---|---|---|
| `backend-ci` | PR, push to main | `backend/**`, `docs/architecture/MODULAR_ARCHITECTURE.md` (module contract test) | format/lint → compile → unit → module/integration (Testcontainers) → architecture tests → OpenAPI generation + breaking-change diff → SCA/secret scan → coverage report |
| `web-ci` (matrix: public-web, vendor-dashboard, admin-dashboard) | PR, push | `apps/<app>/**`, `packages/**` | install (frozen lockfile) → lint → typecheck → unit → build → Playwright (against built app + mocked/staging API) → axe → Lighthouse CI (public-web) |
| `mobile-ci` | PR, push | `client/**` | `flutter analyze` → format check → unit/widget/golden → Android build (debug) → integration tests on emulator (main / nightly) |
| `api-client-sync` | OpenAPI change | `backend/**` | Regenerate TS and Dart clients; fail if generated output not committed or changed unexpectedly |
| `docs-ci` | PR | `docs/**`, `*.md` | Markdown lint, link check, required-doc-update check (see §3) |
| `security-scheduled` | nightly | all | Dependency audit, container scan, ZAP baseline on staging, secret scan history |
| `nightly-contract` | nightly | backend | Provider sandbox contract tests (payments, SMS, email, push) |
| `release-backend` | tag / manual | backend | Build image → scan → sign → push → deploy staging → smoke → manual approval → production |
| `release-web` | tag / manual | per app | Build → deploy staging → smoke/E2E → approval → production |
| `release-mobile` | tag / manual | mobile | Build signed AAB/IPA with secrets from store → upload to TestFlight/Play internal track; staged rollout |
| `infra-plan-apply` | PR / main | `infra/**` | Plan on PR (comment output), apply on main with approval |

## 2. Required checks for merging to `main`

Branch protection: PR required, ≥ 1 approving review (2 for changes in `payment`, `booking`, `inventory`, `identity`, security configuration, or migrations), all relevant pipelines green, up-to-date with main, conversations resolved, no force pushes, signed commits recommended.

## 3. Documentation gate

A PR that changes business rules, module dependencies, database schema, API contracts, security rules or configuration keys must also change the relevant file(s) under `docs/`. CI heuristics warn on obvious misses (e.g., new migration without `DATABASE_DESIGN.md` change; new module dependency without `MODULAR_ARCHITECTURE.md` change); reviewers make the final call ([REVIEW.md](REVIEW.md)).

## 4. Pipeline hygiene

- Pin third-party actions by commit SHA; least-privilege `permissions:` per workflow; no secrets in logs; secrets scoped to environments with protection rules; OIDC to the cloud, no stored cloud keys.
- Caching keyed by lockfiles; reproducible builds; build once, promote the same artifact.
- Concurrency groups cancel superseded PR runs; never cancel deploy jobs mid-flight.
- Fork PRs run without secrets.
- Flaky tests are quarantined per policy ([TESTING_STRATEGY.md](TESTING_STRATEGY.md)).

## 5. Deployment flow

```
merge to main → build & test → image (sha tag) → deploy staging → smoke + E2E → [approval] → production (rolling/blue-green)
                                                    └─ migration job precedes app rollout (expand/contract)
```

Rollback: redeploy previous image digest (migrations are backward compatible); data-fix migrations are forward-only. Release notes generated from Conventional Commits. Feature flags control exposure.

## 6. Metrics

Lead time, deployment frequency, change failure rate, MTTR; pipeline duration budgets (PR feedback ≤ 15 min target); flaky test rate.
