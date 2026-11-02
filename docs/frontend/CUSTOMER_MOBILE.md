# Customer Mobile Application (Flutter + Riverpod)

Directory: `apps/customer-mobile`. Platforms: Android and iOS (Android prioritized for the Gabon market `[OD-23]`). Consumes the customer audience of the backend API and `/ws`.

## 1. Scope

Search, property detail, quote and booking (instant and request), payments (Airtel Money, Moov Money, cards), booking history and cancellation, chat, wishlists, notifications (push + in-app), reviews, loyalty and promos, support/disputes, profile and preferences, language switch (fr/en). Screens map to flows in [USER_FLOWS.md](../product/USER_FLOWS.md).

## 2. Architecture (mandatory)

Clean Architecture with explicit presentation, domain and data boundaries, feature-first. Riverpod is the state-management and dependency-injection mechanism `[ADR-9]`.

```
lib/
  app/                    Bootstrap, router, root composition (PlatformApp)
  core/
    network/              ApiClient, interceptors, ApiErrorMapper (the only place that sees Dio/HTTP exceptions)
    error/                Failure model, FailurePresenter
    platform_ui/          Native UI abstraction: ios/ (Cupertino only), android/ (Material only), adaptive switchers, theme bridge
    l10n/ storage/ logging/ config/
  shared/                 Reusable platform-neutral widgets built from platform_ui, formatters, shared models
  features/<feature>/
    domain/               Entities, repository interfaces, use cases (pure Dart: no Flutter, Riverpod or HTTP)
    data/                 DTO mappers, data sources, repository implementations (no UI imports)
    di/                   Riverpod providers that wire data implementations to domain interfaces
    presentation/         Screens, widgets, Riverpod notifiers (state); depends on domain and di only
```

Dependency rules (enforced by import-boundary lint rules once the app exists, see [STANDARDS_ENFORCEMENT.md](../engineering/STANDARDS_ENFORCEMENT.md)):
- `presentation → domain` and `di`; `di → data + domain`; `data → domain`; `domain → nothing`. Presentation never imports data.
- Features never import other features; shared behavior goes to `shared/` or `core/`. `core` and `shared` never import features.
- Use cases exist only where there is real logic; no pass-through use cases or single-implementation abstractions without a test seam.

Code-organization rules:
- **One responsibility per class and per file**; files ≤ 300 lines (target far smaller); `build` methods small, with sub-widgets extracted into their own files.
- **Strict DRY:** no duplicated business logic or UI components. Before creating a widget, search `shared/` and `core/platform_ui`; a duplicate is a review blocker. Reusable widgets, providers, models, repositories and services live in their proper folders, never inside screen files.
- Immutable models (sealed classes/records/`freezed`); `strict-casts`, `strict-inference`, `strict-raw-types`; no `dynamic`.
- Providers are small and single-purpose; `AsyncNotifier`/`Notifier` for screen state; repositories exposed via providers and overridden in tests.
- Navigation: declarative router (`go_router`) with typed routes, guards and deep links, adapted to native transitions through `platform_ui`.
- API client generated from OpenAPI; no hand-written DTOs duplicating the contract. The app never computes prices: it displays server quotes.
- Real-time: STOMP over WebSocket (`stomp_dart_client`) behind a connection provider with reconnect/backoff; REST polling fallback for payment status and chat history.
- Centralized error handling: [ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md) §4. Features never catch transport exceptions or build error messages themselves.
- Media: images and videos through the shared variant helper (named Cloudinary variants, public cloud name only); uploads use backend-issued signed parameters; no provider secrets in the app ([MEDIA_ARCHITECTURE.md](../architecture/MEDIA_ARCHITECTURE.md)).

## 3. Native platform UI (non-negotiable)

- **iOS renders native Cupertino components and interaction patterns. Android renders native Material components and interaction patterns.**
- **Cupertino widgets must never appear on Android. Material widgets must never appear on iOS.** This covers dialogs, buttons, text fields, switches, pickers, indicators, navigation bars, tab bars, sheets, refresh controls, toasts/snackbars, page transitions and scroll physics.
- All platform-specific UI lives behind one abstraction, `core/platform_ui`:
  - A platform-neutral API (`PlatformApp`, `PlatformPage`, `PlatformNavigationBar`, `PlatformTabBar`, `PlatformButton`, `PlatformTextField`, `PlatformSwitch`, `PlatformDialog`, `PlatformActionSheet`, `PlatformDatePicker`, `PlatformProgress`, `PlatformNotice`, `PlatformListItem`, `PlatformRefresh`, `PlatformPageRoute`, `PlatformScrollBehavior`).
  - Two implementations: `ios/` (imports Cupertino only) and `android/` (imports Material only), and a thin adaptive switcher per component that selects one implementation from a single `PlatformKind` provider resolved inside `platform_ui` (overridable in tests).
  - Design tokens feed both `CupertinoThemeData` and `ThemeData`; features read tokens through a platform-neutral accessor.
- Features use only `package:flutter/widgets.dart` primitives (layout, text, images, gestures) plus `platform_ui` and `shared/`. Importing `material.dart` or `cupertino.dart` outside `platform_ui` is a build failure; so is `Platform.isIOS`/`defaultTargetPlatform` branching outside it; and `ios/` importing Material or `android/` importing Cupertino.
- Adaptive constructors that switch toolkits internally (for example `Switch.adaptive`) and third-party packages that draw platform UI are used only inside `platform_ui`.
- Platform behavior is part of the contract: iOS gets swipe-back, large titles where appropriate, action sheets, Cupertino pickers, bounce physics, no ink ripple; Android gets Material 3 components, system/predictive back, snackbars and bottom sheets, ripple, clamping physics.
- Custom-painted brand components (maps, charts, galleries) are allowed when built from primitives and not imitating the other platform's controls.
- **Tests:** two widget tests per `platform_ui` component (one per platform); a shared helper pumps each screen with `debugDefaultTargetPlatformOverride` set to iOS then Android and fails if the iOS tree contains any Material-library widget type or the Android tree contains any widget whose type name starts with `Cupertino` (maintained deny-list of Material control types for the iOS check); golden images per platform for core components. The static import and platform-branching rules run in CI as lint rules once the app is scaffolded.

## 4. Key behaviors

- **Offline/poor network:** cached last search, wishlist and bookings (read-only offline) using a local DB (e.g., `drift`) or key-value cache; optimistic UI never applies to money or booking state; retries use the same `Idempotency-Key` per user intent (key generated when the intent starts and persisted until resolved).
- **Payment UX:** mobile-money flow shows a "confirm on your phone" waiting state with countdown to `payment_due_at`, handles app backgrounding, listens to `/user/queue/payment-updates` and polls as fallback; card flow uses provider SDK/hosted page; no card data handled by app code.
- **Guest booking:** can book without account; offers account creation after; guest can reopen booking via link from email/SMS (deep link carrying the booking access token, stored in secure storage).
- **Push:** FCM/APNs registration via `ident_device` endpoint; notification taps deep link to booking/chat; foreground messages shown in-app.
- **Security:** tokens in secure storage (Keychain/Keystore); no secrets in the app bundle; optional certificate pinning decision `[OD-24]`; root/jailbreak detection not required initially; obfuscated release builds; screenshots allowed except on payment screens (configurable).
- **Accessibility:** semantics labels, dynamic type, contrast per [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md), minimum 48dp touch targets.
- **Performance:** named media variants from the CDN (see §2), lazy lists with pagination (cursor), cold-start budget `[OD-14]`, app size budget.
- **i18n:** ARB files, `fr` default ([INTERNATIONALIZATION.md](../architecture/INTERNATIONALIZATION.md)).
- **Analytics/crash:** privacy-reviewed events only, consent-gated `[LEGAL]`; crash reporting with PII scrubbing.

## 5. Testing

Unit tests for notifiers/repositories (mocked via provider overrides), error-mapper and interceptor tests, platform-pair widget tests (§3), widget tests per screen with golden tests for core components, integration tests (`integration_test`) for search→book→pay (against a mock server or staging), accessibility checks, test for locale switch. CI: `flutter analyze`, `flutter test`, build for Android/iOS on main. See [TESTING_STRATEGY.md](../engineering/TESTING_STRATEGY.md).

## 6. Release

Flavors `dev`, `staging`, `prod` configured by `--dart-define-from-file` (no secrets inside); signing keys in CI secret store; staged rollout; force-upgrade mechanism driven by a server-provided minimum supported version.
