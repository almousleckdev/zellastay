# Next.js Architecture (public website, vendor dashboard, admin dashboard)

Mandatory for all three web apps. App-specific scope is in [PUBLIC_WEBSITE.md](PUBLIC_WEBSITE.md), [VENDOR_DASHBOARD.md](VENDOR_DASHBOARD.md) and [ADMIN_DASHBOARD.md](ADMIN_DASHBOARD.md); error behavior in [ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md); enforcement in [STANDARDS_ENFORCEMENT.md](../engineering/STANDARDS_ENFORCEMENT.md).

## 1. Principles

1. **Feature-oriented.** Code is organized by business feature, not by technical type at the top level.
2. **One responsibility per file; small files.** At most 280 lines per file (ADR-43) and about 40 per function; oversized files are refactored, not exempted.
3. **No duplication.** UI components, API access, validation schemas and business logic each exist once, in the shared package or in one feature. Duplicates found in review are blockers.
4. **Strict TypeScript.** `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`; no `any`, no `@ts-ignore`; `unknown` plus narrowing at boundaries; API types come from the generated client, runtime validation with shared schemas.
5. **Consistent states.** Every data-dependent view implements loading, error, empty and success states using shared components.
6. **Apps are independent.** Apps never import from each other; sharing happens only through `packages/*`.

## 2. Workspace layout

**Directory names (ADR-44):** the apps live in `web/website` (public site), `web/vendor` and `web/admin`; shared packages in `packages/` are `ui`, `design-tokens`, `icons`, `utilities`, `api-client` and `config`. `schemas`, `i18n` and `media` are added when their first consumer exists. Wherever this document writes `apps/<app>`, read `web/<app>`; the earlier names `public-web`, `vendor-dashboard`, `admin-dashboard` map to `website`, `vendor`, `admin`. Each app uses a `src/` directory. `utilities` holds narrow pure helpers only (class-name merging, locale formatting, accessibility helpers); business logic never goes there. The current `web/*` directories are unmodified `create-next-app` scaffolds with separate lockfiles; they become one pnpm and Turborepo workspace in the workspace step of [DEVELOPMENT_PROCESS.md](../engineering/DEVELOPMENT_PROCESS.md).

```
web/<app>/src/
  app/                    Routes only: page/layout/loading/error/not-found. Thin; compose features
  features/<feature>/
    ui/                   Presentational components (one per file)
    hooks/                Data and interaction hooks (TanStack Query, form state)
    services/             Business logic and orchestration (pure, framework-free where possible)
    api/                  Feature endpoints built on the shared api-client (no raw fetch)
    schemas/              Zod schemas and form validation
    types/                Feature-specific types
    index.ts              Public surface of the feature
  shared/                 App-local shared pieces (layout shells, app providers)
packages/
  ui/                     Design-system components and the state components (ErrorState, EmptyState, Skeleton, AsyncBoundary)
  api-client/             The only place that performs network calls; typed errors; generated endpoints
  schemas/                Validation shared across apps (mirrors server constraints)
  i18n/                   Message catalogs and helpers
  media/                  Variant-based media URL helper (no secrets)
  design-tokens/, config/ Tokens; eslint/tsconfig/tailwind presets
```

Dependency direction inside a feature: `ui → hooks → services → api`; `schemas` and `types` are leaf modules. UI never calls the API client directly; services never import UI. A feature imports another feature only through its `index.ts`; circular feature imports are forbidden.

## 3. Rules by concern

| Concern | Rule |
|---|---|
| Components | One exported component per file; named exports; props typed explicitly; no business rules in components; no inline styles or raw colors/spacing (tokens only) |
| Hooks | Start with `use`; wrap data fetching and state; no JSX; query keys come from a per-feature key factory |
| Services | Hold business logic and calculations; deterministic and unit-tested; never format money or compute prices (the server decides; the client displays) |
| API access | Only through `packages/api-client`. Raw `fetch`/`axios` elsewhere is forbidden. Responses validated against schemas; errors are the typed errors from [ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md) §5 |
| Validation | Zod schemas defined once in `schemas/` (feature) or `packages/schemas` (shared); forms and API inputs reuse the same schema; the server remains authoritative |
| Types | No duplicate DTO definitions; generated API types are the source; domain view types derive from them |
| Server vs client components | Server components by default; `"use client"` only where interaction requires it; secrets and server logic never in client components |
| i18n | No hardcoded user-visible strings; keys in `packages/i18n`; fr default, en supported; no concatenated translations |
| Media | Images/videos via `packages/media` variants and the shared image component; never build Cloudinary transformation strings by hand; never reference Cloudinary secrets; uploads use backend-issued signed parameters ([MEDIA_ARCHITECTURE.md](../architecture/MEDIA_ARCHITECTURE.md)) |
| Accessibility | WCAG 2.2 AA via shared components; axe checks in CI |
| Security | CSP with nonces, no `dangerouslySetInnerHTML` (use the shared sanitizing component), only `NEXT_PUBLIC_*` values that are non-secret |

## 4. Shared packages governance

New shared component or hook: justified by a second use or by design-system scope; documented with states and accessibility notes; covered by tests and a Storybook story. Before writing a component, search `packages/ui` and the feature folders; duplicating an existing one is a review blocker. Shared package changes are versioned in the workspace and run the tests of every consumer.

## 5. Standard states

`AsyncBoundary` composes the four states; features supply only what is specific:

| State | Component | Requirements |
|---|---|---|
| Loading | `Skeleton`/spinner from `packages/ui`, `loading.tsx` per data segment | Layout-stable, announced to assistive tech |
| Error | `ErrorState` fed by a typed error | Localized message, retry action when retryable, `requestId` reference, never raw messages |
| Empty | `EmptyState` | Explains the situation and offers the next action |
| Success | Feature UI | Handles partial data and stale-while-revalidate indicators |

## 6. Enforcement

- **Lint (set up with the first app):** `@typescript-eslint/no-explicit-any`, `ban-ts-comment`, `max-lines` (280, blank lines and comments counted) and `max-lines-per-function` (40), `no-restricted-imports` (raw HTTP clients, other apps, deep imports into other features), `jsx-a11y`, import-cycle detection (`import/no-cycle` or `dependency-cruiser`), and a custom rule for hardcoded user-visible strings.
- **Repository checks (added with the first app):** file size, raw HTML, Cloudinary/secret names and secret-like `NEXT_PUBLIC_*` variables, and env files in apps, enforced through ESLint rules, dependency-cruiser and CI secret scanning.
- **Tests:** component tests for all four states of each data view, API-client unit tests, Playwright journeys, accessibility checks, and a build step with `tsc --noEmit`.
- **Review:** checklist items in [REVIEW.md](../engineering/REVIEW.md).
