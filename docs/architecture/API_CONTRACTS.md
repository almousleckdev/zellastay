# API Contract Sharing (Java, TypeScript, Dart)

The Spring Boot backend is the authority for business rules and domain calculations. Its generated OpenAPI 3.1 document is the **single source of truth** for the API contract. Web and Flutter clients are generated or typed from it. No client hand-copies a DTO, an enum or an error shape.

**Status: Proposed (ADR-46).** Today nothing in the repository generates OpenAPI. The backend has no springdoc dependency. Introducing it is a backend change that needs explicit approval at the moment it is implemented.

## 1. Pipeline

| Step | Mechanism | Notes |
|---|---|---|
| Generate | springdoc-openapi in the backend. A test boots the application against Testcontainers and writes the spec to `contracts/openapi/zellastay.v1.json` | Committed file, reviewed in pull requests |
| Drift gate | CI regenerates the spec and fails if it differs from the committed file | Prevents silent contract changes |
| Breaking-change gate | `oasdiff` (a Go binary; do not install the npm package of that name, which is a placeholder) compares against `main` | Fails unless the API version is bumped; required by [API_SPECIFICATION.md](API_SPECIFICATION.md) §5 |
| TypeScript | `openapi-typescript` for types and `openapi-fetch` under the hand-written `packages/api-client` | The wrapper adds locale, request id, timeouts and the typed errors of [ERROR_HANDLING.md](../engineering/ERROR_HANDLING.md) §5 |
| Dart | A generator chosen by a short spike (`swagger_parser` or `openapi_generator`) | Constraints: it must accept the project's Dart SDK, produce null-safe immutable models and enums |

Generated TypeScript output is produced at build time and not committed. Generated Dart output is committed with a CI freshness check, so Flutter developers do not need the Node toolchain (Proposed).

## 2. What is shared, and how

| Concern | Single source | Consumers |
|---|---|---|
| Endpoints, DTOs, enums (for example booking status) | OpenAPI spec | TypeScript and Dart clients |
| Error contract (RFC 9457 problem+json, stable codes) | Backend error catalog and OpenAPI | `api-client` typed errors; Dart `core/network` `Failure` mapper |
| Money (`amountMinor`, `currency`) | OpenAPI schema | Clients display only; they never compute prices |
| Design tokens | `packages/design-tokens` JSON | CSS variables (web) and Dart constants (Flutter), see [DESIGN_SYSTEM.md](../frontend/DESIGN_SYSTEM.md) |
| Localization | Per-platform catalogs (ICU JSON, ARB); server localizes error text by stable code | A parity check covers shared key namespaces such as `status.*`. A generated single source for UI strings is deferred (OD-39) |
| Business rules | Backend only | Never duplicated in clients |

## 3. Rules

- Consumer-facing schemas are separate DTOs from domain and persistence models.
- Every endpoint documents audience, required permission, idempotency behavior and error codes.
- Additive changes stay in `v1`; breaking changes need `/api/v2`.
- Generated code is excluded from the 280-line rule and from lint, but not from type checking.
- A new endpoint is not accepted without its authorization tests ([TESTING_STRATEGY.md](../engineering/TESTING_STRATEGY.md)).
