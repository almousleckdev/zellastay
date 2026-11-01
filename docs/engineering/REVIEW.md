# Code Review Process and Checklist

Every change is reviewed before it is considered complete — by a human, and by an AI-assisted review pass when an agent authored the change. Authors self-review first using this checklist. Reviews are about correctness, safety and maintainability, not style (style is automated).

## 1. Process

1. Author runs relevant checks locally/CI; PR description states **what, why, how tested, risks, and any checks not executed (with reason)**.
2. PRs are small and single-purpose. Oversized PRs are split.
3. Reviewer reads the linked documentation and the changed tests first (do tests describe the intended behavior?).
4. Required reviewers: module owner(s) of every touched module; second reviewer for `payment`, `booking`, `inventory`, `identity`, `organization` authorization, security config, and database migrations.
5. Blocking findings must be fixed or explicitly waived with a recorded rationale. Non-blocking suggestions are labelled.
6. Merge only when required CI checks are green. Author does not self-approve.
7. Post-merge: docs and configuration deployed; follow-up issues created for deferred items.

For agent-authored work, the agent performs a structured self-review against this checklist before reporting a task done, and lists findings and what was fixed.

## 2. Checklist

### Scope and requirements
- [ ] Change is within approved scope; no invented requirements; links to requirement IDs in [PRD.md](../product/PRD.md).
- [ ] Business rules match product docs; if rules or architecture changed, docs are updated in this PR.
- [ ] Open decisions touched by this change are resolved in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md) or the change is blocked.

### Architecture
- [ ] Module boundaries respected; no new dependency outside [MODULAR_ARCHITECTURE.md](../architecture/MODULAR_ARCHITECTURE.md) §4; no cycles; no cross-module table access.
- [ ] Layering respected: no business logic in controllers; repositories in infrastructure; transactions in application services.
- [ ] No duplicated business logic; no unnecessary abstraction; file and method sizes reasonable; single responsibility.
- [ ] Domain events used for cross-module reactions; listeners idempotent.

### Correctness and data
- [ ] Critical flows transactional; failure paths leave consistent state.
- [ ] Concurrency considered (locks/constraints/idempotency/optimistic locking); race scenarios tested.
- [ ] Money handled via `Money`; currency exponent respected; no floats.
- [ ] Time via injected clock; time zones correct.
- [ ] Migrations backward compatible, constraints present, [DATABASE_DESIGN.md](../architecture/DATABASE_DESIGN.md) updated.
- [ ] External calls have timeouts, retries only where idempotent, outside DB transactions with locks.
- [ ] Notification/provider failures cannot roll back bookings or payments.

### Security and privacy
- [ ] Authorization enforced in application layer; object-level checks (tenant/ownership); deny by default; audience separation.
- [ ] Authorization tests: allowed, denied, cross-tenant.
- [ ] Inputs validated; outputs encoded; no injection vectors; uploads validated.
- [ ] No secrets, tokens or PII in code, logs, errors or events; PII minimized.
- [ ] Audit events emitted for privileged/financial actions.
- [ ] No unverified legal assumption encoded; `[LEGAL]` items flagged.

### Quality
- [ ] Tests written first and meaningful (behavior, edge cases, failure modes); no tests tailored to pass trivially; no skipped tests.
- [ ] No hardcoded business data, URLs, environment settings; configuration via typed properties/DB.
- [ ] No static or randomly generated production data.
- [ ] Naming clear; comments only where *why* is non-obvious; no dead/commented code; no unreferenced TODOs.
- [ ] No AI attribution text in project files.
- [ ] Error handling explicit; messages localized via codes; logs structured without sensitive data.
- [ ] i18n: strings in catalogs (fr and en), no concatenated translations.
- [ ] Accessibility checked for UI changes.
- [ ] Observability: logs/metrics/traces for new flows; alerts or monitors updated if new invariants.
- [ ] Performance: queries use indexes, pagination used, N+1 avoided, no unbounded results.

### Delivery
- [ ] API changes: OpenAPI updated; breaking changes versioned; clients regenerated.
- [ ] Feature flags/config documented; rollout and rollback considered.
- [ ] Checks run listed; checks not run listed with reasons.

## 3. Severity labels

- **Blocker:** security, data integrity/money, tenant isolation, module-boundary or scope violations, missing tests for critical behavior.
- **Major:** correctness risk, missing failure handling, maintainability debt that will compound.
- **Minor:** readability or small improvements.
- **Question/Nit:** clarification or optional.

## 4. Definition of done

Code reviewed with no unresolved blockers or majors; all required checks green; tests cover the change; documentation updated; observability and security requirements met; unexecuted checks reported honestly.
