# Tenancy and Row-Level Security

Tenant = vendor **organization** (ADR-7, [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) §10). A vendor must never read or change another vendor's private data. Isolation is defense in depth: application authorization, explicit tenant scoping in every repository query, and PostgreSQL row-level security (RLS) as a backstop.

**Status:** the design below is **Proposed**. Nothing tenant-owned is implemented, and no tenant-owned table may be created until the RLS spike (§4) passes and its outcome is recorded in ADR-42. Slice A (identity, audit) is unaffected: accounts, sessions and audit events are global tables.

## 1. Layers

1. **Principal and membership.** Access tokens carry no organization or permission claims (ADR-31). Every vendor request names the organization it acts on; the server verifies an `ACTIVE` membership and the member's role and property scope on every request (cached briefly, never trusted from the token).
2. **Application authorization.** Application services receive an explicit `TenantContext` and call a policy object. Controllers cannot be the only barrier. Missing or mismatching objects return 404 so existence is not leaked.
3. **Repository scoping.** Every repository query for tenant data filters by organization. There are no unscoped finders for vendor data.
4. **PostgreSQL RLS.** Policies keyed on a transaction-local setting backstop mistakes in layers 2 and 3.
5. **Tests.** A parameterized cross-tenant suite proves user A of organization X cannot read or mutate any resource of organization Y across every vendor endpoint, and RLS tests run as the real restricted role.

## 2. Organization selection (Proposed)

The organization is selected per request through the route (for example `/api/v1/vendor/organizations/{organizationId}/…`), not a header and not a token claim. Reasons: it is visible in logs and audit, it works with caching keys, it cannot be silently inherited from a previous request, and the route matcher can register it in `AudienceRoutes`. The server checks membership for the path organization on each request. Switching organization in a client clears its caches (see [VENDOR_DASHBOARD.md](../frontend/VENDOR_DASHBOARD.md)). To be confirmed by the spike (ADR-42).

## 3. Transaction-local tenant context

- The application sets the tenant with `set_config('zellastay.organization_id', :id, true)` (transaction-local) at the start of every transaction that touches tenant data, inside that transaction. Never a session-level `SET`, because pooled connections are reused.
- Policies compare the column with `current_setting('zellastay.organization_id', true)`. An unset or empty setting matches nothing (fail closed).
- Policy shapes are not one-liners. Cases the spike must cover:
  - tenant-owned rows: visible only to the matching organization;
  - public catalog rows: readable by anonymous and customer audiences when published and eligible;
  - bookings: visible to the owning customer or guest and to the property's organization;
  - platform administrators: cross-tenant access through an explicit admin path (a separate setting or role), always audited.
- The expression must be evaluated once per statement and stay index-friendly.
- A candidate hook for setting the context is a Spring transaction listener that runs after the transaction begins on the same connection. The spike confirms or replaces it.
- Asynchronous work (event listeners, scheduled jobs) runs without a request. The organization id travels in the event payload and jobs establish an explicit system context. A missing context fails closed.

## 4. RLS spike (mandatory gate before Slice B)

Run in Testcontainers PostgreSQL, using the **restricted application role**, not the migration owner:

1. The application role is not a superuser, does not have `BYPASSRLS`, and does not own the tables. `FORCE ROW LEVEL SECURITY` is enabled where the owner could otherwise bypass policies.
2. Flyway migrations run with an owner role; the application connects with a separate role granted only the privileges it needs.
3. The tenant setting survives only for its transaction: no leakage to the next transaction on a reused pooled connection, with nested transactions (`REQUIRES_NEW`) and with parallel requests.
4. An unset setting returns no rows and rejects writes.
5. Public, participant and admin policy paths behave as specified in §3.
6. Event listeners and scheduled jobs without context fail closed; with the organization in the event they work.
7. Query plans use indexes with the policy in place and overhead is measured on realistic row counts.

**Outcome:** ADR-42 confirms or replaces ADR-7, fixes the organization-selection mechanism, and records the policy templates. If RLS proves unworkable with JDBC transaction management, the fallback is application-level scoping with mandatory tests (risk R-8), recorded by a new ADR before any tenant table exists.

## 5. Database roles

| Role | Purpose | Privileges |
|---|---|---|
| Owner (migration) | Flyway only | Owns schema objects; used only by the migration step |
| Application | Runtime | `SELECT/INSERT/UPDATE/DELETE` on module tables as needed; `SELECT` and `INSERT` only on `audit_event`; no ownership, no `BYPASSRLS`, not a superuser |

Local development and CI currently use one role (see [DEVOPS.md](../engineering/DEVOPS.md) §12); the two-role setup is introduced with the spike and then used in CI so tests exercise the real role. Deployed environments never run the application with owner or superuser credentials.

## 6. Related controls

Tenant-scoped cache keys (`org:{id}:…`), tenant id in log context, tenant-scoped object keys, export endpoints that re-authorize at generation time ([SECURITY_ARCHITECTURE.md](SECURITY_ARCHITECTURE.md) §7). Country scoping for administrators is separate from tenancy: see [MULTI_COUNTRY.md](MULTI_COUNTRY.md) §7.
