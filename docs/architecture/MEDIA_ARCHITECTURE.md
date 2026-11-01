# Media Architecture (Cloudinary)

Owner module: `media`. Cloudinary is the selected provider for property images and galleries, accommodation videos, vendor verification documents and other supported files, user profile images and other application media (ADR-26). Binary files are never stored in PostgreSQL; the database keeps Cloudinary identifiers and metadata.

Facts below come from Cloudinary's public documentation read on 2026-11-01; items marked **[verify]** are not confirmed by the documentation and must be settled in the Phase 1 media spike (OD-29) before dependent work is built.

## 1. Confinement

- All provider access lives in the `media` module. The Cloudinary SDK (`com.cloudinary:cloudinary-http5`; version pinned in `libs.versions.toml`, 2.6.0 at the time of writing) and any `com.cloudinary..` type may be used **only** in `com.zellastay.media.infrastructure` (architecture test).
- `media.domain`/`application` expose a provider-neutral port (`MediaStorage`). Other modules reference media only by `MediaAssetId` and call `MediaApi` (create upload intent, request private URL, attach/release, delete by owner). No other module learns Cloudinary public IDs, delivery types or URLs.
- Frontends receive only: the public cloud name, per-upload signed parameters issued for one upload, and delivery URLs built by the backend or by a shared helper from a public ID and a named variant. **The API secret never leaves the backend** (scanner rule `no-secrets-in-frontend`; secret supplied through `ZS_SECRETS_DIR`/environment).

## 2. Asset policies

Limits are product/ops values `[OD-30]`; formats are the allow-list, never a deny-list.

| Kind | Examples | Visibility (delivery type) | Upload mode | Allowed formats (initial) |
|---|---|---|---|---|
| `PROPERTY_IMAGE` | gallery, covers, room photos | public (`upload`) | signed direct | jpg, png, webp |
| `PROPERTY_VIDEO` | tours | public (`upload`) | signed direct, chunked for large files | mp4, mov, webm |
| `PROFILE_IMAGE` | avatars | public (`upload`) | server-proxied | jpg, png, webp |
| `CHAT_IMAGE` | conversation images | private (`authenticated`) | server-proxied | jpg, png, webp |
| `VERIFICATION_DOCUMENT` | identity and business documents | private (`authenticated`) | server-proxied | pdf, jpg, png |
| `OTHER` | later needs | decided per addition by ADR | — | — |

## 3. Upload flows

### 3.1 Server-proxied upload (documents, avatars, chat images)
1. Client sends multipart to a `media`-owned endpoint; authorization and entitlement/limit checks run first (tenant, role, plan limits).
2. Server validates size, extension/MIME consistency and **magic-number content type**, decodes images and re-encodes them (strips EXIF/GPS), and for documents runs the platform malware scan before anything reaches the provider (own scanner in our control; Cloudinary's Perception Point add-on is documented as an option `[verify plan/price]`).
3. Server uploads with a server-chosen `public_id` (random identifier, never the user's filename) into `zellastay/{environment}/{kind}/{ownerNamespace}/…`, the correct delivery type and tags, then stores the asset row as `READY`.

### 3.2 Signed direct upload (property galleries and videos)
1. Client asks `POST /media/upload-intents` with kind, declared type and size. Server authorizes, checks plan limits (for example maximum photos), creates a `PENDING` asset row with a server-generated `public_id`, and returns a signature payload: `cloud_name`, `api_key`, `timestamp`, `signature`, `public_id`, `folder`, `allowed_formats`, `tags`, `type`, `notification_url`. All values are covered by the signature (the signature covers every upload parameter except `file`, `cloud_name`, `resource_type`, `api_key`), so the client cannot change the destination, formats or public id. Signatures are valid for one hour; intents expire sooner.
2. Client uploads directly to Cloudinary (chunked for large videos; videos over 100 MB require chunked upload per the documentation).
3. Completion is confirmed by (a) the signed Cloudinary webhook (`X-Cld-Signature` + `X-Cld-Timestamp`, verified with the API secret, timestamp window enforced, handler idempotent on public id + version) and (b) the client's completion call as a fallback. The server compares provider-reported `bytes`, `format`, dimensions/duration and `public_id`/folder against the intent. Mismatch or oversize ⇒ **destroy and mark `REJECTED`**.
4. **Why server-side size enforcement:** upload presets do not support a per-preset file size limit, and client-side limits are advisory only. Account-level plan limits are a backstop, not a control.

Unsigned presets are not used: they cannot carry per-upload authorization.

## 4. Access control

- **Public media** (`upload`): served from the CDN; only content that is meant to be public (approved listing imagery, avatars shown to counterparts) uses this type. Listing media becomes public only when the property is eligible; unpublished drafts stay `authenticated` until publication, then are re-typed/re-uploaded `[verify rename between delivery types]`.
- **Private media** (`authenticated`): original and derived versions require a signed URL; the backend generates it per request after the authorization check (ownership/tenant/role, booking status for chat), with a short expiry (≤ 5 minutes; the provider's time-limited download URL defaults to one hour, so the expiry is set explicitly) and writes an audit event for every document access. URLs are never stored or logged.
- **Constraint:** authenticated assets do not allow on-the-fly transformations; thumbnails and previews are produced as eager derivatives at upload time, defined centrally in configuration.
- **Documents on Cloudinary [verify]:** the documentation describes private/authenticated delivery for images and videos; it does not state whether it applies to `raw` files, nor how PDFs are classified. The spike must prove that PDFs and scans can be stored and delivered as authenticated assets with signed, expiring URLs. If not, the fallback options (convert pages to images, or a different private store for documents via a new ADR) are decided before Phase 1 onboarding ships.
- Token-based access requires the provider's Advanced plan and is not needed by this design.

## 5. Delivery and optimization

- Frontends never build arbitrary transformation strings. A fixed catalog of **named variants** (for example `card`, `gallery`, `hero`, `thumb`, `avatar`) with fixed aspect ratios and widths is defined once (configuration + shared helper `mediaUrl(publicId, variant)` generated for TypeScript and Dart) using `f_auto`/`q_auto`, responsive width sets for `srcset`, and a streaming-friendly profile for video.
- Delivery of arbitrary unsigned transformations is restricted in the provider settings to prevent abuse and cost surprises `[verify setting and plan]`.
- Server stores `width`, `height` and `blur` placeholder data where provided so UIs avoid layout shift ([DESIGN_SYSTEM.md](../frontend/DESIGN_SYSTEM.md) §6).

## 6. Persistence

`media_asset` (see [DATABASE_DESIGN.md](DATABASE_DESIGN.md)) stores provider, `public_id`, resource type, delivery type, version, format, bytes, width/height/duration, content hash/etag when returned, kind, visibility, owner reference (`owner_type`, `owner_id`, `organization_id`), status `PENDING | READY | REJECTED | ORPHANED | DELETING | DELETED`, intent expiry and timestamps. Unique on `(provider, resource_type, delivery_type, public_id)`. Gallery ordering/captions stay in `catalog` (`cat_property_media` references the asset id).

## 7. Deletion, replacement and orphan cleanup

- **Delete:** when an owner removes media (or the owner is deleted/erased), the asset becomes `DELETING`; a worker calls the provider's destroy with `invalidate=true` (CDN copies may otherwise remain reachable for minutes), is idempotent, retries with backoff, then marks `DELETED`. Personal-data erasure uses the same path, subject to legal holds `[LEGAL]`.
- **Replace:** never overwrite a public id. Upload the replacement as a new asset, switch the reference in one transaction, then delete the old asset. This avoids stale CDN caches and gives atomic rollbacks.
- **Backups:** if provider-side backup is enabled, destroyed assets remain recoverable there; backup is disabled for private documents or a purge procedure is defined `[verify][OD-29]`.
- **Orphans:** (1) `PENDING` intents past expiry are destroyed by their known public id and removed; (2) assets no longer referenced by any owner become `ORPHANED`, then `DELETING` after a grace period; (3) a scheduled reconciliation lists provider assets under the environment's folder prefix (Search/Admin API: rate-limited and eventually consistent) and reports assets unknown to the database. It reports first and only auto-deletes after the report has proven reliable. The database is the source of truth; the provider is never listed to decide what exists.
- Metrics and alerts: pending-intent age, rejected uploads, delete failures, reconciliation drift ([OBSERVABILITY.md](../engineering/OBSERVABILITY.md)).

## 8. Environments, configuration and secrets

- Separate Cloudinary environments (or accounts) for development, staging and production; credentials are never shared; folder prefix includes the environment name.
- Configuration (backend only unless noted): `ZS_CLOUDINARY_CLOUD_NAME` (public value, may be exposed through configuration endpoints), `ZS_CLOUDINARY_API_KEY`, `ZS_CLOUDINARY_API_SECRET` (secret, from the secret manager/mounted file). These variables are added to the configuration contract ([DEVOPS.md](../engineering/DEVOPS.md) §12) when the media module is implemented; until then they are not required by the application.
- Tests and CI never use production credentials; provider contract tests run nightly against a dedicated test environment.

## 9. Compliance and data location

Cloudinary's documentation states storage defaults to US data centers, with EU storage available to Enterprise accounts only, and lists SOC 2 Type II and a DPA. Hosting identity documents with a third party and its sub-processors, and the region, are legal and procurement questions `[LEGAL]` L-18, `[OD-29]`. No verification documents are uploaded to any production environment until L-18 is closed.

## 10. Testing

Unit tests for signature construction against documented vectors and for URL/variant generation; fake `MediaStorage` for module tests; integration tests for intent → webhook → ready, oversize rejection with destroy, expired-intent cleanup, replacement, deletion retry, private URL authorization (allowed, denied, cross-tenant) and audit; architecture tests for SDK confinement; scanner rules for secrets in frontends; nightly provider contract tests.
