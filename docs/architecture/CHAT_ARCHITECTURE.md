# Chat Architecture

Owner module: `chat`. Real-time messaging between a customer (or guest) and the vendor team of the property, **strictly tied to a booking or booking request**.

## 1. Business rules

1. A conversation exists **only** for a booking in a chat-eligible status. One conversation per booking (`chat_conversation.booking_id` unique).
2. Participants: the booking's customer (account or guest) and vendor users who are ACTIVE members of the property's organization with permission `chat.use` and property scope covering the property. Staff scope changes take effect immediately for subsequent access.
3. Vendors can message customers about their reservations (any chat-eligible booking). Customers cannot start conversations with a vendor without a booking/request. No cold messages in either direction.
4. Message types: text and image attachments. No other file types at launch.
5. Delivery status (`SENT`, `DELIVERED`), read receipts, unread counters per participant (customer; per vendor user plus organization-level unread for shared inbox), message history with pagination.

## 2. Access by booking status

| Booking status | Conversation state | Send | Read history |
|---|---|---|---|
| `REQUESTED` | ACTIVE | customer, vendor | yes |
| `PENDING_PAYMENT` | ACTIVE | customer, vendor | yes |
| `CONFIRMED`, `CHECKED_IN` | ACTIVE | customer, vendor | yes |
| `COMPLETED` | ACTIVE until `completed + N days` (default proposal 14), then READ_ONLY | both during window | yes |
| `DECLINED`, `EXPIRED` | READ_ONLY | none | yes during retention |
| `CANCELLED`, `NO_SHOW` | READ_ONLY after short grace (proposal 7 days) to settle refunds | both during grace | yes |
| Open dispute on booking | ACTIVE for parties; admin can read | both | yes |

The status → state mapping is a pure function in `chat.domain`, driven by booking events (`chat` listens to booking events and also checks the booking status via `BookingApi` at connect/subscribe/send time, never trusting a cached flag alone). The N-day windows are platform parameters `[OD-21]`.

## 3. Authorization

Enforced at every layer, on **every** operation (not only on connect):
- STOMP `CONNECT`: validate JWT (customer, vendor) or guest booking access token (scoped to one booking) → build principal.
- STOMP `SUBSCRIBE`: only to the principal's own `/user/queue/**` destinations; no shared `/topic` for conversations. Attempts to subscribe elsewhere are rejected.
- STOMP `SEND` to `/app/conversations/{id}/…` and REST history/attachment calls: a `ChatAccessPolicy` loads the conversation and checks (a) participant relationship, (b) conversation state permits the action, (c) vendor permission and property scope (re-evaluated, with a short TTL cache keyed by membership version), (d) rate limits.
- Cross-tenant tests: a vendor in organization X must never read or send in a conversation of organization Y, even with a valid conversation id.
- Admins read conversations only through the admin API for dispute/abuse handling; each access is audit-logged with reason.

## 4. Transport

- WebSocket + STOMP at `/ws` (Spring messaging). Heartbeats 10 s; client reconnect with exponential backoff; token refresh before expiry; session closed on token expiry or account suspension.
- Message flow:
  1. Client sends `/app/conversations/{id}/send` with `clientMsgId` (UUID) and body or attachment refs.
  2. Server authorizes, validates (length, rate, content rules), persists in one transaction (unique `(conversation_id, clientMsgId)` makes retries idempotent), updates unread counters, writes outbox event `ChatMessageSent`.
  3. After commit, server delivers to online participants' queues and returns `SENT` ack to the sender.
  4. Recipient client confirms `delivered` (STOMP frame) ⇒ server updates receipt, notifies sender. `read` frames advance the read cursor and zero the unread counter.
  5. Offline recipients: notification module receives `ChatMessageSent` and sends push/in-app per preferences (with batching/debounce to avoid spam).
- History and recovery: `GET /conversations/{id}/messages?cursor=` is the source of truth. After reconnect, clients fetch messages newer than their last known id. WebSocket delivery is best-effort; persistence is authoritative.

### 4.1 Multi-instance fan-out
Spring's simple in-memory broker only knows sessions on its own instance. A `ChatDispatcher` publishes `{recipientKey, envelope}` to a Redis pub/sub channel; every instance subscribes and forwards to local sessions via `SimpMessagingTemplate.convertAndSendToUser`. Redis loss ⇒ real-time degrades; messages remain persisted and are fetched on reconnect/poll. If scale demands, replace the bridge with a STOMP broker relay (new ADR).

### 4.2 Presence and typing
Typing indicators are ephemeral (not persisted, rate-limited). Presence is optional and limited to "last seen" visibility rules `[OD-21]`; default: no online presence exposure to customers.

## 5. Attachments (images)

- Upload via REST to `media` (server-proxied multipart), type allow-list (JPEG, PNG, WebP, HEIC converted), size limit, magic-number validation, re-encode, EXIF stripped, malware scan hook, status `READY` before it can be attached to a message.
- Stored as Cloudinary `authenticated` assets namespaced by conversation; delivered via short-lived signed URLs after the same access policy check. Thumbnails are eager derivatives created at upload (authenticated assets do not allow on-the-fly transformations).
- Quotas per conversation/day to limit abuse.

## 6. Unread counters and receipts

- `chat_read_cursor` per participant stores last read message and a materialized unread count (incremented on message persist, reset on read). For vendor teams, per-user cursors plus an organization-level aggregate for the property inbox.
- Receipts per message and recipient (`delivered_at`, `read_at`); group semantics for vendor staff: a message counts as read for the customer-facing receipt when any authorized vendor staff has read it.
- Total unread badge is served via REST and pushed on `/user/queue/unread`.

## 7. Moderation, abuse and safety

- Rate limiting (messages per minute/hour), max length, link policy (configurable; block off-platform payment solicitations via pattern rules `[OD-21]`), user report function (creates a support case with message snapshot), block-by-admin, soft-delete (`deleted_at`) for moderation with audit.
- System messages (`SYSTEM`) inserted for state changes (e.g., booking confirmed), localized at render time from a message code.

## 8. Retention and privacy

- Messages are retained for a platform-configured period after the conversation becomes READ_ONLY; after that, bodies are deleted or anonymized and attachments purged by a retention job, **except** conversations attached to open disputes/legal holds. Durations are `[LEGAL]` / `[OD-21]`.
- User erasure requests: messages are anonymized (sender shown as deleted user) where law allows; counterpart's copy is handled per policy `[LEGAL]`.
- Chat content is never used for marketing; never logged; access by staff audit-logged.
- Encryption at rest through storage layer; end-to-end encryption is out of scope (platform needs moderation capability).

## 9. Data model

See [DATABASE_DESIGN.md](DATABASE_DESIGN.md) (`chat_*` tables). Indexes: `(conversation_id, created_at desc, id)` for history; `(organization_id, last_message_at desc)` for vendor inbox; unique `(conversation_id, client_msg_id)`.

## 10. Scalability and testing

- Connection budget per instance (documented load test target `[OD-14]`); message persistence is a single small insert; fan-out cost bounded by two-party conversations plus staff.
- Tests: authorization matrix per status and role (including guest and cross-tenant), idempotent resend, ordering, reconnect recovery, multi-instance fan-out (two app instances + Redis in Testcontainers), unread counter correctness under concurrent sends/reads, attachment access control, retention job.
