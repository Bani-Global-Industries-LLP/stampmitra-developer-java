# Changelog

All notable changes to this project are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/) (pre-1.0: minor versions may contain breaking changes).

## [Unreleased]

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
