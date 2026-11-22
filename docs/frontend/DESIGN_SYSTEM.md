# Design System

One design language across four apps, implemented twice: **web** (`packages/ui`, React + design tokens) and **mobile** (Flutter, with native Cupertino implementations on iOS and native Material implementations on Android behind `design_system`), both generated from a single source of **design tokens**. Brand identity (name treatment, logo, final palette, typography choices) is a business/design input `[OD-26]`; this document defines structure and rules, and the values below are placeholders until brand approval.

## 1. Principles

1. Trust first: this is a payments and travel product — clarity, honest pricing, visible policies.
2. Low-bandwidth and mid-range devices: lightweight components, restrained imagery, fast first paint.
3. Accessible by default: WCAG 2.2 AA; touch targets ≥ 44 px web / 48 dp mobile.
4. Localization-proof: layouts survive French text expansion (+30%) and future RTL.
5. Consistency over novelty: reuse components; new components require a spec.
6. Dashboards optimize for density and scanning; consumer apps optimize for imagery and conversion.

## 2. Tokens (source of truth: `packages/design-tokens`, JSON)

Format: Style-Dictionary-compatible JSON compiled to CSS variables (web, Tailwind theme), and Dart constants (mobile). No raw hex/px values in app code.

| Category | Tokens |
|---|---|
| Color | `brand.*`, `neutral.0–950`, semantic: `success`, `warning`, `danger`, `info`, surface/background/text/border roles, light and dark themes, state colors (hover/pressed/disabled/focus). Contrast pairs verified in CI. |
| Typography | Font family stack with system fallbacks and subsetted web font(s); scale (display, h1–h4, body-lg/md/sm, label, caption, mono for references); line-heights; numeric tabular figures for money |
| Spacing | 4-pt scale (`space.1 = 4`…) |
| Radius / Elevation | `radius.sm/md/lg/full`; shadow levels 0–4 |
| Motion | Durations and easings; reduced-motion variants |
| Breakpoints | `sm 640`, `md 768`, `lg 1024`, `xl 1280`, `2xl 1536` |
| Z-index | Named layers |

## 2a. Approved palette and contrast rules (ADR-44)

Shared by web and Flutter, defined once in `packages/design-tokens`. **Approved:** primary Orange-500 `#F97316`; background white `#FFFFFF`; primary text Neutral-900; secondary text Neutral-600; borders Neutral-200; neutral gray surfaces only where necessary; light theme first. No random colors, no unnecessary gradients, no excessive shadows, no oversized controls, one icon set.

**Proposed (contrast, computed by hand; the CI contrast test is authoritative):**
- White on Orange-500 is about 2.8:1 and fails WCAG 2.2 AA for text and for UI components. Neutral-900 on Orange-500 is about 6.4:1 and passes. Primary buttons therefore use a Neutral-900 label on an Orange-500 fill.
- Orange text, links and focus rings on white also fail, so the system defines a darker brand token (around orange-700, about 5:1 on white) for those uses. Orange-500 remains the brand fill.
- Neutral-200 borders (about 1.3:1) are decorative only (cards, dividers). Form-control borders use a stronger neutral token so the control boundary is perceivable (3:1 minimum).
- Semantic colors for success, warning, danger and info (booking and form states) come from one minimal approved set, each pair tested; they are not chosen ad hoc in components.
- Dark mode is out of scope until after launch; the scaffold's dark-mode media query is removed.

## 3. Component inventory

Foundations: Button, IconButton, Link, Input, TextArea, Select, Combobox, Checkbox, Radio, Switch, DatePicker, DateRangePicker (stay selection, property time zone aware), PhoneInput (E.164, country list), MoneyInput/MoneyText, OTPInput, Stepper, Tabs, Accordion, Tooltip/Popover, Modal/Sheet (bottom sheet on mobile), Toast, Banner, Badge, Tag, Avatar, Skeleton, EmptyState, ErrorState, Pagination/InfiniteList, Table (dashboards), FormField with error summary.

Domain components: PropertyCard, PropertyGallery, AmenityList, PriceBreakdown (always from server quote), RatingStars/ReviewCard, AvailabilityCalendar, BookingStatusBadge (single mapping from booking state to color/label), PaymentMethodPicker, PaymentStatusPanel (pending/success/failure with countdown), CancellationPolicyView, MapView/MapPin, ChatThread/MessageBubble/Composer/ReceiptIndicator/UnreadBadge, NotificationItem, VerificationStatusStepper, PlanCard/EntitlementMeter, StatTile/Chart (dashboards), PermissionGate (UI hint only).

Each component spec: purpose, anatomy, variants, states (default, hover, focus, active, disabled, loading, error), accessibility behavior (roles, keyboard, announcements), i18n notes, and tests. Components live in Storybook (web) and a widget catalog (Flutter); visual regression via snapshot/golden tests.

## 4. Patterns

- **Money:** always displayed through `MoneyText` using currency exponent and locale formatting; XAF shown without decimals; tabular numerals.
- **Dates:** property-local dates for stays, with explicit time zone note when user's zone differs.
- **Forms:** inline validation on blur, summary on submit, preserve input on error, clear error codes mapped from API problem details.
- **Loading:** skeletons for content, spinners only for short actions; never block on non-critical data.
- **Errors:** friendly localized messages mapped from stable API error codes; always offer next action; show `requestId` in a copyable "technical details" disclosure for support.
- **Destructive actions:** confirmation with explicit consequence text (e.g., refund amount).
- **Status language:** consistent labels for booking, payment, verification, subscription states across apps (single i18n namespace `status.*`).
- **Empty states** guide the next step (e.g., vendor with no property → "Add your first property").
- **Dark mode:** supported via tokens (consumer apps at launch is optional `[OD-26]`; dashboards later).

## 5. Accessibility specifics

Color is never the only signal; focus rings visible (≥ 3:1); form labels persistent; error text associated via `aria-describedby`; modal focus trapping; live regions for payment/chat updates; semantic landmarks; text resizable to 200%; Flutter `Semantics` for custom widgets; screen-reader testing on TalkBack and VoiceOver for booking and payment flows.

## 6. Iconography and imagery

Single icon set (open license, outline style) shared via token names; amenity icons mapped from `cat_amenity.icon`. Photos: server-generated responsive variants (WebP/AVIF), blur-hash placeholders, aspect ratios fixed (e.g., 4:3 cards, 16:9 hero) to avoid layout shift. Alt text from captions; decorative images marked.

## 7. Required state and error components

Both platforms provide the same behavior contract: `ErrorState`/`ErrorView`, `EmptyState`, loading skeletons, inline field errors and a transient notice (toast/snackbar equivalent). Web versions live in `packages/ui`; mobile versions are implemented per platform in `design_system`. They render the localized server `detail` and never raw exceptions ([ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md)). Media uses named variants only ([MEDIA_ARCHITECTURE.md](../architecture/MEDIA_ARCHITECTURE.md) §5).

## 8. Governance

Design tokens and components are versioned; breaking changes follow semantic versioning in the workspace; changes need design + engineering review; Storybook/catalog deployed for review on each PR (no production data).
