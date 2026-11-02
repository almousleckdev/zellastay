# Event Catalog

Domain events published through the transactional outbox (Spring Modulith event publication registry). Rules for naming, payloads and versioning: [MODULAR_ARCHITECTURE.md](MODULAR_ARCHITECTURE.md) §6. Event types live in the publisher's `api.events` package. A module may only consume events of modules it is allowed to depend on (§4 of that document); `ModuleDependencyRulesTest` enforces this.

Common envelope on every event: `eventId`, `occurredAt`, `schemaVersion`, `correlationId`. Payloads carry identifiers and minimal facts only, never secrets or full personal data.

Consumers marked `*` are planned; the event is defined only when the consuming feature is implemented. Events marked **time** are published by a scheduled job in the publishing module.

| Publisher | Event | Trigger | Key payload | Consumers |
|---|---|---|---|---|
| platformconfig | `ConfigurationChanged` | Versioned config change | key, scope, version | search*, pricing* |
| identity | `AccountRegistered` | Account created | accountId, kind, locale | notification |
| identity | `VerificationCodeRequested` | Email verification requested | accountId, purpose, channelHint (**never the code**; the secret is passed through the `VerificationDelivery` port, ADR-30) | notification |
| identity | `PasswordResetRequested` | Reset requested | accountId (never the reset token) | notification |
| identity | `AccountSuspended` / `AccountReinstated` / `AccountDeactivated` | Admin action, or the user closing the account | accountId | chat*, organization* |
| media | `MediaReady` / `MediaRejected` | Scan/processing finished | mediaId, ownerRef | catalog*, onboarding*, chat* |
| onboarding | `VerificationSubmitted` | Case submitted | caseId, subjectRef, country | notification |
| onboarding | `VerificationDecided` | Approve or reject | caseId, subjectRef, decision, reasonCodes | organization, catalog, subscription, notification |
| subscription | `SubscriptionStateChanged` | Any lifecycle transition | organizationId, from, to, effectiveAt | search, notification, catalog*, reporting* |
| subscription | `SubscriptionRenewalDue` (**time**) | Before period end | organizationId, dueAt | notification |
| organization | `OrganizationCreated` | Registration | organizationId, country, legalType | reporting* |
| organization | `OrganizationStatusChanged` | Approved/suspended/closed | organizationId, status | catalog, notification |
| organization | `MemberInvited` / `MemberRoleChanged` / `MemberDeactivated` | Staff changes | organizationId, memberId, role | notification, chat |
| catalog | `PropertyListingChanged` | Property approved, published, unpublished, edited, suspended | propertyId, organizationId, visibility, version | search, inventory*, engagement*, notification |
| inventory | `UnitTypeChanged` | Structure change | propertyId, unitTypeId | search, pricing* |
| inventory | `AvailabilityChanged` | Calendar edits, holds/allocations released | unitTypeId, dateRange | search |
| pricing | `RatesChanged` | Rate plan/rules edited | unitTypeId, version | search |
| promotion | `PromoRedeemed` / `PromoReversed` | Redemption lifecycle | redemptionId, bookingRef, amount | loyalty* |
| promotion | `PromotionExpiringSoon` (**time**) | Voucher/campaign near end | accountId, voucherId | notification |
| booking | `BookingRequested` | Request created | bookingId, propertyId, organizationId, customerRef, mode | notification, chat |
| booking | `BookingApproved` | Vendor approved, inventory reserved | bookingId, paymentDueAt | notification, chat |
| booking | `BookingDeclined` | Vendor declined | bookingId, reasonCode | notification, chat |
| booking | `BookingPaymentDue` / `BookingExpiringSoon` (**time**) | Deadline approaching | bookingId, dueAt | notification |
| booking | `BookingConfirmed` | Payment succeeded and allocated | bookingId, propertyId, stay dates, totals | notification, chat, reporting, loyalty |
| booking | `BookingCancelled` | Cancelled by customer/vendor/admin/system | bookingId, cancelledBy, refundDue | notification, chat, reporting, loyalty |
| booking | `BookingExpired` | Request or payment window elapsed | bookingId, from | notification, chat |
| booking | `BookingCheckedIn` / `BookingNoShow` | Vendor action or rule | bookingId | chat, reporting |
| booking | `BookingCompleted` | Stay ended | bookingId, propertyId, customerRef | review, loyalty, chat, reporting |
| booking | `BookingStayReminderDue` (**time**) | Before check-in | bookingId, checkIn | notification |
| payment | `PaymentSucceeded` / `PaymentFailed` / `PaymentExpired` | Provider result applied | paymentId, reference(type,id), amount, currency | booking, subscription, notification |
| payment | `PaymentAnomalyDetected` | Late/duplicate success | paymentId, reference, kind | booking, notification (admin) |
| payment | `RefundRequested` / `RefundSucceeded` / `RefundFailed` / `RefundNeedsManualHandling` | Refund lifecycle | refundId, paymentId, reference, amount | booking, support, notification |
| payment | `SettlementPaid` / `SettlementFailed` | Provider settlement report/event | settlementId, organizationId, amount | notification, reporting |
| payment | `ReconciliationExceptionRaised` | Unmatched item | itemId, outcome, amount | notification (admin) |
| chat | `ChatMessageSent` | Message persisted | conversationId, messageId, recipientRefs | notification |
| chat | `ConversationStateChanged` | Active/read-only/closed | conversationId, state | (internal) |
| review | `ReviewInvited` (**time**) | After completion delay | bookingId, customerRef | notification |
| review | `ReviewPublished` / `RatingAggregateChanged` | Publish/moderate | reviewId, propertyId, rating | notification, search |
| support | `DisputeOpened` / `DisputeResolved` | Dispute lifecycle | disputeId, bookingId, resolution | notification |
| loyalty | `PointsEarned` / `ReferralRewarded` | Accrual/referral | accountId, points | notification |

Notes:
- `PropertyListingChanged` carries a single computed `visibility` (the one definition of listing eligibility lives in catalog; consumers never recompute it).
- Dispute outcomes that need money movement are executed by `support` calling `PaymentApi.requestRefund` (support → payment is allowed); booking and payment never depend on support.
- `PaymentSucceeded` is the only trigger for `BookingConfirmed`; booking verifies amount and currency against its own total before confirming.
- `BookingCancelled` informs consumers; the refund itself is requested synchronously by booking via `PaymentApi.requestRefund` in the cancellation transaction.
- Operational alerts (`ProviderCircuitOpen`, `OutboxBacklogHigh`) are metrics/alerts, not domain events.
- Retention of processed publications: completed outbox rows are archived after a configurable period; deletion respects the audit and legal-hold rules in [SECURITY_ARCHITECTURE.md](SECURITY_ARCHITECTURE.md).
