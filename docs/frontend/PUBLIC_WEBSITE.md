# Public Booking Website (Next.js + TypeScript)

Directory: `apps/public-web`. Audience: visitors, customers, guests, and prospective vendors (vendor self-registration lives here).

## 1. Scope

Home, destination landing pages, search results (list + map), property detail, quote/checkout, payments, guest and customer booking management, account area (bookings, wishlists, chat, notifications, loyalty, profile), static/legal pages, help center, **"List your property" vendor registration and onboarding** (individual/business, documents, first property draft, submission status/resubmission).

Note: after approval, vendors operate in the Vendor Dashboard ([VENDOR_DASHBOARD.md](VENDOR_DASHBOARD.md)); the public site links to it.

## 2. Architecture

The shared Next.js standards (feature structure, shared components, small files, strict types, one API client, consistent states) in [WEB_ARCHITECTURE.md](WEB_ARCHITECTURE.md) are mandatory; error handling follows [ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md) §5; media follows [MEDIA_ARCHITECTURE.md](../architecture/MEDIA_ARCHITECTURE.md).

- Next.js (App Router), TypeScript `strict`, React Server Components for public SEO pages, client components only where interactivity is needed.
- Rendering strategy: ISR/SSR for property and destination pages (cache tags revalidated on catalog events via a signed revalidation webhook), SSR for search with edge caching of anonymous queries, CSR for authenticated account areas.
- Data access: generated TypeScript client from OpenAPI (shared package `packages/api-client`); server-side fetch with request-scoped locale; TanStack Query for client-side caching/mutations.
- Auth: token handling through a Backend-for-Frontend layer (Next.js route handlers) storing tokens in httpOnly, Secure, SameSite cookies; the browser never reads raw tokens. WebSocket chat obtains a short-lived WS token from the BFF.
- Forms: schema validation with a shared schema library (e.g., Zod) mirroring server constraints; server remains authoritative.
- i18n: locale-prefixed routing (`/fr`, `/en`), `next-intl`, localized slugs and metadata ([INTERNATIONALIZATION.md](../architecture/INTERNATIONALIZATION.md)).
- Maps: provider abstraction (provider decision `[OD-25]`); lazy loaded; respects consent.
- Shared code: `packages/ui` (design system components), `packages/api-client`, `packages/i18n-utils`, `packages/config` (eslint/tsconfig/tailwind tokens). Apps remain independently deployable; shared packages are versioned within the workspace.

## 3. SEO and performance

- Semantic HTML, structured data (`LodgingBusiness`/`Hotel`/`Offer`, `BreadcrumbList`, `AggregateRating`), canonical URLs, `hreflang`, XML sitemaps (property, destination) per locale, robots rules (no indexing of checkout/account).
- Core Web Vitals budgets (LCP/INP/CLS) set in CI via Lighthouse CI `[OD-14]`; image optimization via CDN variants, `next/image` with explicit sizes; font subsetting; low-bandwidth friendly (important for Gabon connectivity): compressed assets, minimal JS on public pages, skeleton loading.
- Only approved, published, eligible properties are rendered; non-eligible return 404/410 and are removed from sitemaps.

## 4. Booking and checkout UX rules

- Price breakdown shown from the server quote with expiry timer; any change in price/availability triggers an explicit confirmation step.
- Guest checkout minimal fields; account creation optional afterward.
- Payment: mobile-money number entry with country-aware validation, pending state with live status; card entry in provider-hosted component; 3-D Secure handling.
- Booking pages for guests are reachable by tokenized links; tokens are not kept in URLs after first load (exchanged for a cookie scoped to that booking).
- Cancellation shows server-computed refund preview.

## 5. Security

CSP with nonces, no inline scripts without nonce, dependency audit in CI, `rel=noopener`, strict CORS on API, input sanitization for user-generated content (reviews, chat) rendered as text, rate-limited forms with bot protection, no secrets in client bundles (only public env vars prefixed per Next.js rules), file uploads for vendor onboarding go through pre-signed upload endpoints with client-side type/size checks and server verification.

## 6. Accessibility

WCAG 2.2 AA: keyboard navigation, focus management in dialogs/carousels, labelled form controls, error summaries, color contrast, reduced motion, accessible map alternative (list), automated checks (axe) in CI plus manual audits per release.

## 7. Testing and quality

Vitest/Jest + Testing Library for components, Playwright for E2E (search → book → pay with provider sandbox/mocks, vendor registration, locale switching), visual regression for design-system components, Lighthouse CI, axe. ESLint (strict), Prettier, `tsc --noEmit` in CI. See [TESTING_STRATEGY.md](../engineering/TESTING_STRATEGY.md).

## 8. Deployment

Containerized (standalone output) or platform-native hosting `[OD-15]`; CDN in front; environment configuration via runtime env; preview deployments per PR (no production data).
