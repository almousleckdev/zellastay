# API Specification

Conventions and endpoint catalog. The OpenAPI 3.1 document generated from code (springdoc, with contract tests) becomes the machine-readable source once implementation begins; this document defines the rules it must follow and the resource map. Client SDKs for web (TypeScript) and mobile (Dart) are generated from the OpenAPI file in CI.

## 1. Conventions

- Base path `/api/v1`. Breaking changes ⇒ `/api/v2`; additive changes stay in v1. Deprecations announced with `Deprecation`/`Sunset` headers.
- **Audience-separated route prefixes**, each with its own Spring Security filter chain, token audience and rate-limit policy:

| Prefix | Audience | Authentication |
|---|---|---|
| `/api/v1/public/**` | Anonymous visitors | none (rate-limited) |
| `/api/v1/me/**`, `/api/v1/bookings/**`, … (customer resources) | Customers and guests | customer JWT, or guest booking access token for single-booking routes |
| `/api/v1/vendor/**` | Vendor users | vendor JWT + organization context |
| `/api/v1/admin/**` | Platform admins | admin JWT (separate issuer audience, MFA required) |
| `/ws` | Customers and vendors | JWT in STOMP CONNECT |
| `/webhooks/{provider}` | Providers | signature verification, IP allow-list where supported |

- JSON, UTF-8, `camelCase`. Dates: ISO 8601; stay dates `YYYY-MM-DD` (property local), instants in UTC `Z`.
- Money: `{ "amountMinor": 150000, "currency": "XAF" }`. Never decimal strings or floats.
- Language: `Accept-Language` (fr default, en); responses localize content; `Content-Language` returned.
- IDs: UUID strings. Public booking reference `ZS-XXXXXXXX` is for humans.
- **Pagination:** cursor-based (`?limit=&cursor=`), response `{ items, nextCursor }`. Max limit enforced.
- **Filtering/sorting:** explicit allow-listed query params; no generic query DSL.
- **Idempotency:** mutating endpoints that create money- or inventory-affecting resources (`POST /bookings`, `POST /bookings/{id}/payments`, refunds, subscription purchase) **require** `Idempotency-Key` (UUID). Same key + same body ⇒ original response; same key + different body ⇒ `422 idempotency_key_reuse`. Keys retained ≥ 24 h.
- **Concurrency:** `ETag`/`If-Match` on editable resources (property, rates); stale ⇒ `412`.
- **Errors:** RFC 9457 `application/problem+json` produced only by the central handler: `{ type, title, status, detail, code, requestId, errors?:[{field, code, message}] }`. `code` is a stable machine code from the owning module's catalog (`booking.unavailable`, `auth.forbidden`…); `title`/`detail`/field messages are localized by the server (fr default, `Accept-Language` en); codes are not. `requestId` matches the `X-Request-Id` response header and the structured logs. No stack traces, class names, SQL, rejected values or internal identifiers ever appear. Full standard, categories and client behavior: [ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md).
- **Status codes:** 200/201/202/204; 400 validation; 401 unauthenticated; 403 unauthorized; 404 (also used to hide existence across tenants); 405/406/413/415 protocol errors; 409 conflict (state/inventory, concurrent modification); 412 stale; 422 business rule; 429 rate limit with `Retry-After`; 500 unexpected; 503 retryable dependency/lock contention. The category-to-status mapping lives in one place.
- **Validation:** Bean Validation at the web layer for shape; domain rules in domain/application.
- **Rate limits:** per IP and per principal by endpoint class (auth strictest); Redis token bucket.
- **Observability headers:** `X-Request-Id` accepted/returned; W3C `traceparent` propagated.

## 2. Authentication endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/auth/register` | Customer registration (email or phone) |
| POST | `/api/v1/auth/verify` | Verify email/phone code |
| POST | `/api/v1/auth/login` | Returns access + refresh token |
| POST | `/api/v1/auth/refresh` | Rotating refresh; reuse detection revokes family |
| POST | `/api/v1/auth/logout` | Revoke refresh token |
| POST | `/api/v1/auth/password/forgot`, `/reset` | |
| POST | `/api/v1/vendor/auth/login`, `/api/v1/admin/auth/login` | Separate audiences; admin requires second factor |
| POST | `/api/v1/vendor/auth/register` | Vendor self-registration (individual/business) |

## 3. Resource catalog

Only the resource map is fixed here; field-level schemas come from OpenAPI.

### Public / customer
| Resource | Operations |
|---|---|
| `GET /public/countries`, `/public/locales`, `/public/amenities`, `/public/property-types` | Reference data |
| `GET /public/properties/search` | Destination, dates, guests, filters, map bounds, sort, cursor |
| `GET /public/properties/{id}`, `/{id}/reviews`, `/{id}/availability` | Detail, reviews, calendar summary |
| `POST /quotes` | Create server-side quote for unit type + dates + guests (+ promo code); returns `quoteId`, lines, `expiresAt` |
| `POST /bookings` | Create booking from `quoteId` (Idempotency-Key). Instant ⇒ `PENDING_PAYMENT`; request ⇒ `REQUESTED` |
| `GET /bookings`, `GET /bookings/{id}` | Customer history / detail (guest: via access token) |
| `POST /bookings/{id}/cancel`, `GET /bookings/{id}/cancellation-preview` | Cancellation with refund preview |
| `POST /bookings/{id}/payments` | Initiate payment (method, phone/card details via provider tokenization); `GET /payments/{id}` status |
| `GET/POST/DELETE /me/wishlists…` | Favorites |
| `GET/PATCH /me`, `/me/notification-preferences`, `/me/devices` | Profile, preferences, push token |
| `GET /me/notifications`, `POST …/{id}/read` | In-app inbox |
| `GET /me/loyalty`, `POST /promo-codes/validate` | Loyalty and promos |
| `POST /bookings/{id}/review`, `GET /me/reviews` | Reviews |
| `POST /bookings/{id}/disputes`, `GET/POST /support/tickets…` | Support |
| `GET /bookings/{id}/conversation`, `GET /conversations/{id}/messages`, `POST /conversations/{id}/attachments` | Chat REST (history, attachment upload) |

### Vendor (`/api/v1/vendor`)
| Resource | Operations |
|---|---|
| `/organization`, `/organization/members`, `/organization/invitations` | Org profile, staff, roles, property scopes |
| `/onboarding/cases`, `/onboarding/cases/{id}/submit` | Documents and submission, status, rejection reasons |
| `/properties`, `/properties/{id}` (+ `/media`, `/amenities`, `/publish`, `/unpublish`) | Property CRUD within scope |
| `/properties/{id}/unit-types`, `/units` | Inventory structure |
| `/unit-types/{id}/calendar` (GET/PUT ranges), `/blocks` | Availability, blocks |
| `/unit-types/{id}/rate-plans`, `/rules` | Pricing |
| `/bookings`, `/bookings/{id}`, `/approve`, `/decline`, `/cancel`, `/check-in`, `/no-show` | Reservations. `approve` reserves inventory for the payment window and returns `409 booking.unavailable` if the dates are no longer free |
| `/conversations…` | Chat |
| `/promotions` | Campaigns (if entitled) |
| `/reviews`, `/reviews/{id}/response` | Review responses |
| `/subscription`, `/subscription/plans`, `/subscription/change`, `/invoices` | Subscription management |
| `/reports/financial`, `/reports/…/export` | Financial reports |
| `/settlements` | Settlement history |

### Admin (`/api/v1/admin`)
| Resource | Operations |
|---|---|
| `/verification/cases`, `/{id}/claim`, `/approve`, `/reject` | Verification queue |
| `/organizations`, `/properties`, `/accounts` | Search, suspend, reinstate |
| `/config/countries`, `/currencies`, `/locales`, `/taxes`, `/service-fees`, `/parameters`, `/document-requirements` | Platform configuration |
| `/subscription/plans`, `/prices`, `/entitlements` | Plan management |
| `/payments`, `/refunds`, `/reconciliation/runs`, `/settlements` | Finance operations |
| `/bookings` | Support view, permitted interventions |
| `/disputes`, `/tickets` | Support |
| `/templates`, `/notifications/deliveries` | Notification templates & delivery monitoring |
| `/reviews/moderation` | Moderation |
| `/audit/events` | Audit search |
| `/reports/…` | Platform reports |

### Media (owned by the `media` module)
| Resource | Operations |
|---|---|
| `POST /media/upload-intents` | Authorize and create a signed direct-upload payload for galleries/videos (server-chosen `public_id`, folder, formats; no secrets) |
| `POST /media` (multipart) | Server-proxied upload for avatars, chat images and verification documents (validated and scanned server-side) |
| `POST /media/{id}/complete` | Client confirmation; the verified webhook is authoritative |
| `POST /media/{id}/access` | Short-lived signed URL for private media after authorization (audited) |
| `DELETE /media/{id}` | Request deletion/replacement through the owning feature |

Owners (catalog, onboarding, chat, identity) expose their own endpoints for attaching media; clients never address Cloudinary public IDs. Design: [MEDIA_ARCHITECTURE.md](MEDIA_ARCHITECTURE.md).

### Webhooks
`POST /webhooks/{providerKey}` — verify signature, persist raw event (dedupe), respond 2xx quickly, process asynchronously. Payment providers and `cloudinary` (upload notifications, verified with `X-Cld-Signature`/`X-Cld-Timestamp`).

## 4. Real-time (WebSocket + STOMP)

- Endpoint `/ws` (WebSocket; SockJS fallback only if needed by clients). Authentication at STOMP `CONNECT` with a short-lived access token; token expiry closes the session, clients reconnect with a refreshed token.
- Destinations (all subscriptions authorized by an interceptor):
  - Client → server: `/app/conversations/{id}/send`, `/app/conversations/{id}/typing`, `/app/conversations/{id}/read`, `/app/conversations/{id}/delivered`.
  - Server → client (per-user queues, never open topics): `/user/queue/messages`, `/user/queue/receipts`, `/user/queue/unread`, `/user/queue/booking-updates`, `/user/queue/payment-updates`, `/user/queue/notifications`.
- Message envelope: `{ type, version, eventId, occurredAt, payload }`. At-least-once delivery; clients dedupe by `eventId`/`clientMsgId`; REST history endpoint is the recovery path after reconnect.
- Full rules: [CHAT_ARCHITECTURE.md](CHAT_ARCHITECTURE.md).

## 5. Contract governance

- OpenAPI is generated and diffed in CI; breaking diffs fail the build unless version is bumped.
- Consumer-facing schemas are separate DTOs from domain/persistence models.
- Every endpoint documents: audience, required permission, idempotency behavior, error codes.
- Every endpoint has an authorization test (allowed, denied, cross-tenant) — see [TESTING_STRATEGY.md](../engineering/TESTING_STRATEGY.md).
