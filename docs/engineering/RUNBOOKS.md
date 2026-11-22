# Runbooks

Operational procedures for the Railway deployment ([RAILWAY_DEPLOYMENT.md](RAILWAY_DEPLOYMENT.md)). They are written before the first deployment and rehearsed in staging before production. A runbook is not accepted until someone other than its author has followed it. Each step names the verification that proves it worked.

## 1. Deploy

1. Confirm the pull request checks and the staging deployment of the same image are green.
2. Approve the production environment in GitHub. The pipeline deploys the **same image digest** that ran in staging.
3. Watch the deploy healthcheck, then the readiness path, then error rate and latency for 15 minutes.
4. If any alert fires, roll back (§2). Record the release in the change log.

## 2. Roll back an application

1. Redeploy the previous image digest of the affected service (Railway keeps recent images for rollback; retention differs by plan, so do not rely on old images: the registry is authoritative).
2. Do not roll back the database. Migrations are backward compatible with the previous release (expand/contract). If a migration caused the fault, ship a forward fix.
3. Verify with the readiness path and a smoke journey. Open an incident review.

## 3. Database migration safety

- Forward-only, reviewed, expand/contract. No destructive step without an approved migration and recovery plan.
- Before a risky migration: confirm a fresh backup and that point-in-time recovery is active, and record the timestamp.
- Migrations run in the dedicated migration step with the owner role, never from the runtime service.

## 4. Restore PostgreSQL

Used for data corruption or accidental deletion. Railway restores to a **new sibling service** and leaves the source untouched.

1. Declare an incident; stop writers if corruption is ongoing.
2. Choose the target timestamp (point-in-time recovery) or backup.
3. Restore to a sibling service. Do not overwrite the source.
4. Verify on the restored copy: schema version, row counts, and the invariant monitors in [OBSERVABILITY.md](OBSERVABILITY.md) §3 (no overlapping reservations, ledger balanced, confirmed bookings have succeeded payments).
5. Cut over by changing the backend's connection variables to the restored service; keep the old service until verified.
6. If the volume or project is lost, restore from the independent `pg_dump` in the external bucket (decrypt with keys held outside Railway).
7. Record measured RPO and RTO.

**Drills:** before launch, quarterly and after major schema changes, into a disposable isolated service destroyed afterwards. Raw production data is never loaded into staging.

## 5. Rotate secrets

| Secret | Procedure |
|---|---|
| JWT signing key | Add the new key with a new key id while keeping the old public key in `retired-public-keys` until the longest access-token lifetime has passed; switch signing; remove the old key later |
| MFA encryption key | Add the new key id, re-encrypt stored seeds, then retire the old key |
| Challenge pepper | Rotation invalidates outstanding codes; schedule it and announce a short resend window |
| Database passwords | Create the new credential, update the variable, redeploy, verify, revoke the old one |
| Redis password | Update both sides in one deploy; verify rate limiting and the session cache |
| Cloudinary, payment and webhook secrets | Provider-specific; add the new secret, switch, revoke the old; webhook secrets accept both during overlap |

After any rotation: confirm no secret appears in logs, and update the access log in the incident or change record.

## 6. Incident response

1. Detect (alert, uptime monitor or report); assign an incident lead; open a log of times and actions.
2. Classify severity; page for the conditions in [OBSERVABILITY.md](OBSERVABILITY.md) §2.
3. Contain first (for example suspend an account or provider, revoke sessions, disable a feature flag), then diagnose.
4. Communicate: status for users if needed; legal and regulator notification timelines are `[LEGAL]` (L-5).
5. Resolve, verify, and hold a blameless review with actions tracked to closure.

Specific playbooks to write before launch: credential leak (rotate everything the secret could reach, review audit), suspected tenant breach (identify scope through audit and RLS logs, notify), payment discrepancy (freeze payouts for the affected provider, reconcile), provider outage (circuit breaker state, manual flows), Redis loss (confirm degradation is safe), certificate or domain failure.

## 7. Service recovery

| Failure | Behavior and action |
|---|---|
| Backend crash loop | Roll back (§2); check startup guard messages (missing settings are named) |
| PostgreSQL unavailable | The backend becomes unready; restore per §4 if data is damaged |
| Redis unavailable | Rate limiting falls back to per-instance counters and sessions fall back to the database; fix and verify, no data restore needed |
| Provider unavailable | Circuit breaker; payments stay pending and are reconciled; notifications retry |
| Domain or certificate error | Check the DNS records Railway displays for the service (CNAME and TXT), then re-verify |
