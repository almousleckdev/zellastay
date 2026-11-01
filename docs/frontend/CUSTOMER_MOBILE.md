# Customer Mobile Application (Flutter + Riverpod)

Directory: `apps/customer-mobile`. Platforms: Android and iOS (Android prioritized for the Gabon market `[OD-23]`). Consumes the customer audience of the backend API and `/ws`.

## 1. Scope

Search, property detail, quote and booking (instant and request), payments (Airtel Money, Moov Money, cards), booking history and cancellation, chat, wishlists, notifications (push + in-app), reviews, loyalty and promos, support/disputes, profile and preferences, language switch (fr/en). Screens map to flows in [USER_FLOWS.md](../product/USER_FLOWS.md).

## 2. Architecture

Feature-first with clean layering per feature:

```
lib/
  app/                 bootstrap, router, theme, DI root (ProviderScope overrides)
  core/                http client, auth/session, error model, l10n, logging, config, utils
  features/<feature>/
    data/              API clients (generated), DTOs, repositories (impl)
    domain/            entities, repository interfaces, use cases (only where logic exists)
    presentation/      screens, widgets, Riverpod notifiers/providers
  shared/              design-system widgets, formatters
```

- **State management: Riverpod** (`flutter_riverpod`, code generation with `riverpod_generator` for providers `[ADR-9]`). `AsyncNotifier`/`Notifier` for screen state; providers are the DI mechanism; repositories exposed via providers and overridden in tests.
- Immutable models (`freezed` or Dart records/sealed classes); strict analysis (`flutter_lints` + stricter custom rules, `strict-casts`, `strict-inference`, `strict-raw-types`).
- Navigation: declarative router (`go_router`) with typed routes, auth guards, deep links (booking, chat, property).
- API client generated from OpenAPI (`openapi-generator` Dart-dio) — no hand-written DTOs duplicating the contract.
- Money, dates and time zones handled with the shared formatting utilities; the app never computes prices — it displays server quotes.
- Real-time: STOMP client over WebSocket (`stomp_dart_client`) managed by a connection provider with reconnect/backoff and token refresh; falls back to REST polling for payment status and chat history when disconnected.

## 3. Key behaviors

- **Offline/poor network:** cached last search, wishlist and bookings (read-only offline) using a local DB (e.g., `drift`) or key-value cache; optimistic UI never applies to money or booking state; retries use the same `Idempotency-Key` per user intent (key generated when the intent starts and persisted until resolved).
- **Payment UX:** mobile-money flow shows a "confirm on your phone" waiting state with countdown to `payment_due_at`, handles app backgrounding, listens to `/user/queue/payment-updates` and polls as fallback; card flow uses provider SDK/hosted page; no card data handled by app code.
- **Guest booking:** can book without account; offers account creation after; guest can reopen booking via link from email/SMS (deep link carrying the booking access token, stored in secure storage).
- **Push:** FCM/APNs registration via `ident_device` endpoint; notification taps deep link to booking/chat; foreground messages shown in-app.
- **Security:** tokens in secure storage (Keychain/Keystore); no secrets in the app bundle; optional certificate pinning decision `[OD-24]`; root/jailbreak detection not required initially; obfuscated release builds; screenshots allowed except on payment screens (configurable).
- **Accessibility:** semantics labels, dynamic type, contrast per [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md), minimum 48dp touch targets.
- **Performance:** image sizes from CDN variants, lazy lists with pagination (cursor), cold-start budget `[OD-14]`, app size budget.
- **i18n:** ARB files, `fr` default ([INTERNATIONALIZATION.md](../architecture/INTERNATIONALIZATION.md)).
- **Analytics/crash:** privacy-reviewed events only, consent-gated `[LEGAL]`; crash reporting with PII scrubbing.

## 4. Testing

Unit tests for notifiers/repositories (mocked via provider overrides), widget tests per screen with golden tests for core components, integration tests (`integration_test`) for search→book→pay (against a mock server or staging), accessibility checks, test for locale switch. CI: `flutter analyze`, `flutter test`, build for Android/iOS on main. See [TESTING_STRATEGY.md](../engineering/TESTING_STRATEGY.md).

## 5. Release

Flavors `dev`, `staging`, `prod` configured by `--dart-define-from-file` (no secrets inside); signing keys in CI secret store; staged rollout; force-upgrade mechanism driven by a server-provided minimum supported version.
