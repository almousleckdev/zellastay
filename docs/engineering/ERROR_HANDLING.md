# Centralized Error Handling

Mandatory for the backend, the Flutter app and every Next.js app. One error contract, one place that produces it, one place per client that consumes it. Enforcement status for each rule: [STANDARDS_ENFORCEMENT.md](STANDARDS_ENFORCEMENT.md).

## 1. Principles

1. **One contract.** Every API error is an RFC 9457 `application/problem+json` body with the same fields ([API_SPECIFICATION.md](../architecture/API_SPECIFICATION.md) §1).
2. **One producer.** Only the central handler creates error responses. Controllers and application code never build error bodies and never catch exceptions to translate them.
3. **One consumer per client.** Flutter translates every failure once in `core/network`; Next.js apps translate once in `packages/api-client`. Features never inspect HTTP status codes or raw exceptions.
4. **Nothing internal leaks.** No stack traces, exception class names, SQL, table or constraint names, file paths, hostnames, secrets or rejected input values reach a client, ever. Developer messages exist only in logs.
5. **Stable codes, localized text.** Machine-readable `code` values never change meaning. User-facing `title`/`detail` are localized by the server (fr default, en) from message bundles keyed by code.
6. **Every error is traceable.** Each response carries a `requestId` that also appears in the structured logs of the request.

## 2. The API error contract

```json
{
  "type": "urn:zellastay:error:booking.unavailable",
  "title": "Action impossible",
  "status": 422,
  "detail": "Ces dates ne sont plus disponibles.",
  "code": "booking.unavailable",
  "requestId": "0b7c0d6e-5f5a-4d6a-9a71-3b1f3f0f7d11",
  "errors": [{ "field": "checkIn", "code": "validation.not-null", "message": "Ce champ est obligatoire" }]
}
```

`errors` appears only for validation failures and never contains the rejected value. `requestId` echoes the validated `X-Request-Id` request header or a generated identifier; the same value is returned in the `X-Request-Id` response header.

### 2.1 Categories and status codes

The category is a property of the error code, the status is derived in one place (`ErrorStatuses`).

| Category | HTTP | Typical use |
|---|---|---|
| VALIDATION | 400 | Invalid input, malformed request |
| UNAUTHENTICATED | 401 | Missing/expired credentials |
| FORBIDDEN | 403 | Authenticated but not allowed (authorization, tenancy) |
| NOT_FOUND | 404 | Missing resource; also used to hide existence across tenants |
| METHOD_NOT_ALLOWED / NOT_ACCEPTABLE / UNSUPPORTED_MEDIA_TYPE / PAYLOAD_TOO_LARGE | 405 / 406 / 415 / 413 | Protocol-level problems |
| CONFLICT | 409 | State/inventory conflicts, concurrent modification, uniqueness |
| PRECONDITION_FAILED | 412 | Stale `If-Match` |
| BUSINESS_RULE | 422 | Valid request that violates a domain rule |
| RATE_LIMITED | 429 | Throttling |
| INTERNAL | 500 | Unexpected failure |
| UNAVAILABLE | 503 | Dependency or lock contention; safe to retry |

### 2.2 Error codes

- Format `<namespace>.<problem>` in lowercase kebab-case. The namespace is the owning module (`booking.unavailable`) or one of the shared namespaces (`request`, `resource`, `auth`, `validation`, `data`, `service`, `rate`, `internal`).
- Each module declares its codes as an `enum ... implements ErrorCode` (code + category). Codes are never built from strings at throw sites.
- Every code needs `error.<code>.title` and `error.<code>.detail` in both `messages/errors.properties` (French, default) and `messages/errors_en.properties`. If a code has no specific text the category text is used, but the build fails until the specific text exists.
- Codes are never removed or repurposed; deprecated codes stay in the catalog.

## 3. Backend (Spring Boot)

Implemented in the `shared` kernel (`com.zellastay.shared` and `shared.web`):

| Component | Responsibility |
|---|---|
| `ErrorCode`, `ErrorCategory`, `CommonErrorCode` | Typed codes and categories |
| `ZellaStayException` (abstract) | Base of every domain/application exception. Carries an `ErrorCode`, optional safe message arguments and a developer-only message |
| `RequestIdFilter` | Validates or generates the request id, sets MDC `requestId`, sets the response header, always clears MDC |
| `ProblemFactory` | The only builder of `ProblemDetail`s: localizes title/detail, adds `code`, `requestId`, `type` |
| `ApiExceptionHandler` (`@RestControllerAdvice`) | Maps domain exceptions, validation, malformed input, routing/protocol errors, database errors and anything unexpected |
| `ApiErrorController` | Same contract for errors raised outside MVC (container-level errors); Boot's whitelabel page is disabled and `server.error.include-*` is `never` |

Rules:
- Domain and application code throws specific `ZellaStayException` subclasses (for example `BookingUnavailableException`). Every `Throwable` declared under `com.zellastay` must extend `ZellaStayException` (architecture test).
- Infrastructure adapters translate third-party and technical failures at the boundary (a payment provider timeout becomes a domain exception with an `UNAVAILABLE` code); raw provider exceptions never propagate.
- Controllers contain no `try/catch` and no `@ExceptionHandler`; only `@RestControllerAdvice` classes in a `web` package may declare handlers (architecture tests).
- Validation: Bean Validation at the web layer produces `validation.failed` with per-field `errors`; domain invariants throw domain exceptions.
- Database: unique/integrity violations become `data.conflict`; optimistic-lock failures become `data.concurrent-modification` (409); transient failures (lock timeout, query timeout) become `service.busy` (503); everything else is `internal.error`. Only the exception type and SQLState are logged, not statement text or values.
- Unexpected exceptions: logged once at ERROR with the stack trace; the response is the generic `internal.error` with the `requestId`.
- Authentication/authorization (arrives with the `identity` module): Spring Security entry points and access-denied handlers must delegate to `ProblemFactory` so 401/403 share the contract.
- Idempotent replays return the stored response unchanged (errors included).

### 3.1 Logging and correlation

- Structured JSON logs (ECS). `requestId` is a top-level field on every log line written during the request (MDC); `traceId` is added when tracing is enabled.
- 4xx outcomes are logged at INFO without stack traces; 5xx at ERROR with the stack. Messages never contain secrets, tokens, OTPs, card data or personal data.
- Outbound calls (providers) and asynchronous work started from a request propagate the `requestId` as the correlation id ([OBSERVABILITY.md](OBSERVABILITY.md)).

### 3.2 Backend tests (implemented in Phase 0 extension)

Handler behavior for every category, French/English text, validation shape, malformed input, routing errors, database errors without leakage, request-id validation, secret/stack non-disclosure; end-to-end request-id-in-logs test; bundle completeness and parity; code catalog (format, uniqueness, namespace, translations); architecture rules listed in [STANDARDS_ENFORCEMENT.md](STANDARDS_ENFORCEMENT.md).

## 4. Flutter

Location: `lib/core/network` (translation) and `lib/core/error` (models, presentation helpers). Nothing else may import Dio/http exceptions.

- **Failure model** (sealed class `Failure`): `OfflineFailure`, `TimeoutFailure`, `UnauthorizedFailure`, `ForbiddenFailure`, `NotFoundFailure`, `ValidationFailure(fieldErrors)`, `ConflictFailure`, `BusinessRuleFailure(code)`, `RateLimitedFailure`, `ServerFailure(requestId)`, `UnknownFailure`. All carry the server `code`, localized `detail` and `requestId` when available.
- **One translator:** an `ApiErrorMapper` converts transport exceptions and problem+json bodies into `Failure`s. A single Dio interceptor chain handles: connectivity errors, timeouts, `401` (one token refresh then retry, otherwise sign-out), problem+json parsing, and unparseable bodies (→ `ServerFailure`/`UnknownFailure`).
- **Repositories** (data layer) return `Result<T>` (sealed Success/Error) or throw only `Failure`; the domain layer exposes `Failure`, never HTTP types. Notifiers expose `AsyncValue` with the `Failure`.
- **Presentation:** one `FailurePresenter` maps `Failure` to user text: client-owned localized strings for client-side failures (offline, timeout, unknown), and the server's localized `detail` for API errors (no duplicated translation tables). Reusable widgets: `ErrorView` (full screen/section with retry), `InlineFieldErrors`, `ErrorNotice` (banner/toast). Platform-native rendering is delegated to `core/platform_ui` ([CUSTOMER_MOBILE.md](../frontend/CUSTOMER_MOBILE.md) §3). A global `ErrorListener` handles cross-cutting outcomes (session expiry, forced upgrade).
- **Never:** `try/catch` around repository calls in features to build messages, `e.toString()` in the UI, showing `requestId` outside an explicit "technical details" disclosure, swallowing errors silently.
- **Tests:** mapper tests for every status/exception, interceptor tests (refresh, retry once, sign-out), widget tests for `ErrorView`/`ErrorNotice` on both platforms, a test that no feature file references Dio or HTTP exceptions (enforced by the standards scanner).

## 5. Next.js

Location: `packages/api-client` (shared by the three web apps) and `packages/ui` (error components).

- **Typed errors:** `ApiError` (`kind`, `status`, `code`, `title`, `detail`, `requestId`, `fieldErrors`), `NetworkError`, `TimeoutError`, `UnexpectedError`; `kind` mirrors the backend categories. A discriminated union and type guards (`isApiError`, `isValidation`) replace string checks.
- **One client:** all network calls go through the shared client, which sets locale and `X-Request-Id`, applies timeouts, parses problem+json, validates responses with schemas, and throws the typed errors. Raw `fetch`/`axios` outside `api-client` is forbidden (standards scanner).
- **Presentation:** `toUserMessage(error, t)` returns localized text (server `detail` for API errors, client catalog for network/timeout/unexpected). Form libraries receive `fieldErrors` through one helper.
- **Boundaries:** every app defines `app/error.tsx`, `app/global-error.tsx`, `app/not-found.tsx` and per-segment `loading.tsx`/`error.tsx` where data is fetched, all built from the shared `ErrorState`, `EmptyState`, `Skeleton` components. TanStack Query has one global error handler (session expiry redirect, toast for unexpected errors).
- **Server rendering:** server components catch `ApiError` once in a shared helper: `NOT_FOUND` → `notFound()`, `UNAUTHENTICATED` → sign-in redirect, others rethrown to the boundary. The boundary renders a generic message plus the `requestId` ("reference") — never `error.message`, stacks or digests from unknown errors.
- **States:** every data view renders loading, error, empty and success states from shared components ([WEB_ARCHITECTURE.md](../frontend/WEB_ARCHITECTURE.md) §5).
- **Tests:** unit tests for the problem+json parser and type guards, component tests for each state, Playwright tests for 404, 500, offline and expired-session flows, test that error boundaries never render raw messages.

## 6. Adding a new error

1. Add the constant to the module's `ErrorCode` enum (code + category).
2. Add `error.<code>.title`/`detail` in the French and English bundles.
3. Throw a specific `ZellaStayException` subclass from the domain/application layer.
4. Add the code to the module's API documentation if clients react to it specially.
5. Add a test that triggers it through the API and asserts status, code and absence of internals.
