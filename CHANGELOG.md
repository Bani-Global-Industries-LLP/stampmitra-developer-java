# Changelog

All notable changes to this project are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/) (pre-1.0: minor versions may contain breaking changes).

## [Unreleased]

## [0.2.0] - 2026-11-24

Tracks API contract 1.2.0 (`spec/openapi.json`): 62 operations (was 31).

### Added

- e-Stamp: `client.estamp()` gains `getStatesAvailability`, `getState`, `getStateRequirements`, `listDistricts`, `listSros` and
  `getWallet`; `client.estamp().orders()` has `validate`, `create`, `list`, `get`, `cancel`, `export`, `listStamps` and
  `uploadDocument` (`multipart/form-data`, PDF/JPEG/PNG up to 10 MB); `client.estamp().stamps()` has `get`, `cancel`, `replace`,
  `setPanCard` (HTTP 202) and `getCertificate`.
- Wallets and account: `client.wallets().stampDuty()` and `.serviceFee()` (`getBalance`, `topup`, `listTransactions`,
  `listInvoices`, `getInvoice`), `client.account().getBalance()` and `getUsage()`. Stamp Duty top-up amounts keep their JSON
  types (`baseAmount`, `gstAmount`, `totalCharged` numbers; the rest strings); the SDK never computes fees.
- Typed request values `EstampOrderRequest`, `EstampStampLine`, `EstampParty` (bare name or object), `EstampPropertyArea`,
  `DocumentUpload`, `EstampPanCardRequest`, `WalletTopupRequest`, with local checks for the documented limits (1 to 5000 lines,
  one state, `quantity` 1 to 1000, https `panCardImage`, 10 MB upload).
- `StampMitraException.getFieldErrors()` (`error.details.errors[]` as `FieldIssue`), `getWallet()` (`STAMP_DUTY` or
  `SERVICE_FEE` on a 402) and `getDetails()`.
- Typed models for the new responses and for the nested objects of the identity results (`GstinSplitAddress`,
  `DrivingLicenseDetails`, `UdyamNicCode`, ...); the identity results gained `verificationId` and the new registry fields.
- Examples `EstampOrderFlow` and `EstampWallets`; table-driven replay of all 196 fixtures of the new operations.

### Changed

- **Retries now follow the spec markers.** Order cancel, stamp cancel, stamp replace, PAN card link, both wallet top-ups and
  webhook replay are never retried, **even when you pass an idempotency key** (previously a replay with a caller key was
  retried). `POST /v1/estamp/orders/validate` is retried without a key. A 409 `IDEMPOTENCY_IN_PROGRESS` is retried after
  `Retry-After` when a key was sent. `Retry-After` is honoured on 429, 503 and that 409.
- HTTP 422 (`IDEMPOTENCY_KEY_REUSED`) raises `ValidationException` instead of the base `StampMitraException`.
- `EstampDutyCalculateRequest` now rejects a `stampDutyAmount` of 0 or less locally (the spec says greater than 0).
- The identity result getter `getGstin()` reads `gstin` and falls back to `GSTIN`.

## [0.1.0] - 2026-10-16

### Added

- Initial release: `StampMitra` client with `verify()`, `services()`, `estamp()`, `orders()`, `payments().intents()`,
  `usage()`, `logs()` and `webhooks().events()` resources covering all 31 operations of the API snapshot
  (`spec/openapi.json`), each with a synchronous and a `CompletableFuture` (`...Async`) variant.
- Immutable typed models generated from the spec (`scripts/generate.mjs`) that preserve unknown fields
  (`getRaw()`, `get(key)`), plus request builders for orders, payment intents, duty calculation and e-Stamp parties.
- Typed unchecked exceptions, automatic retries with full-jitter backoff and `Retry-After`, auto `Idempotency-Key` on every
  POST except webhook replay (reused across retries), per-request `RequestOptions`, response metadata, lazy log pagination
  (`iterate`, `stream`, `pages`), client-side validators, redacted debug logging.
- Webhook helpers: `Webhook.verifySignature`, `constructEvent`, `constructEventFromHeaders`, `sign`; typed
  `WebhookEvent` / `WebhookEventType` for the eight `order.*` events.
- Pluggable `HttpTransport` (default: JDK `java.net.http.HttpClient`, redirects never followed).
- Tests: 600+ unit tests and a conformance suite that runs the spec repo's mock server; examples (verify PAN, create order,
  logs pagination, async, JDK webhook receiver, Spring Boot controller, OkHttp transport) executed in CI.
