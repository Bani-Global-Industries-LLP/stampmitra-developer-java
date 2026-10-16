# StampMitra Java SDK

[![CI](https://github.com/Bani-Global-Industries-LLP/stampmitra-developer-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Bani-Global-Industries-LLP/stampmitra-developer-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/in.stampmitra/stampmitra-java)](https://central.sonatype.com/artifact/in.stampmitra/stampmitra-java)
[![Java](https://img.shields.io/badge/java-11%2B-blue)](pom.xml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

> The Maven Central badge shows a version only once the package is published.

Official Java client for the [StampMitra Developer API](https://developer.stampmitra.in/docs):
Indian identity verification (PAN, GSTIN, vehicle RC, Voter ID, driving licence, passport, CIN, Udyam, bank account)
and e-Stamp Paper. OpenAPI spec: <https://developer.stampmitra.in/openapi.json>.

> **Status: pre-1.0 (0.1.0).** The API surface may change in minor releases. Pin your version.

SDK guide: <https://developer.stampmitra.in/sdks/java> | API docs: <https://developer.stampmitra.in/docs>

## Install

```xml
<dependency>
  <groupId>in.stampmitra</groupId>
  <artifactId>stampmitra-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

```kotlin
// Gradle
implementation("in.stampmitra:stampmitra-java:0.1.0")
```

Requires **Java 11 or newer** (built with `--release 11`; the test suite runs on JDK 11, 17 and 21). Runtime
dependencies are only Jackson (`jackson-databind`, `jackson-datatype-jsr310`). HTTP uses the JDK's built-in
`java.net.http.HttpClient`; no HTTP library is pulled in, and you can plug in another one (see
[Custom HTTP client](#custom-http-client)). No Lombok, no annotation processors.

## Quickstart

```java
import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.models.PanVerification;

StampMitra client = StampMitra.builder().apiKey(System.getenv("STAMPMITRA_API_KEY")).build();
PanVerification pan = client.verify().pan("ABCDE1234F", "Rahul Kumar");
System.out.println((pan.isValid() ? "valid " : "invalid ") + pan.getNameMatchResult() + " " + pan.getReferenceId());
```

## Authentication

Keys look like `sk_test_...` (free sandbox) or `sk_live_...` (metered). They are read from the
`STAMPMITRA_API_KEY` environment variable unless you call `apiKey(...)`. Keys that do not match
`^sk_(test|live)_[A-Za-z0-9_-]{8,}$` are rejected unless `allowCustomKey(true)`. `client.environment()` is
`"test"` or `"live"`.

> **Security:** server-side only. Never ship `sk_` keys in Android apps, desktop installers or anything a user can unpack,
> and never commit them.

Builder options: `apiKey`, `baseUrl`, `timeout` (30 s per attempt), `maxRetries` (2), `transport`, `defaultHeader`,
`appInfo(name, version, url)`, `logger`, `debug`, `validateInput` (true), `allowCustomKey`, `executor` (for the async
methods), `sleeper` (tests). `baseUrl` must be `https://` (plain `http://` is accepted only for `localhost`, `127.0.0.1`
and `::1`, e.g. a mock server) and must not contain credentials. Every method also accepts per-request
`RequestOptions`: `timeout`, `maxRetries`, `idempotencyKey`, `header(name, value)`.

## Resources

| Resource | Methods | API |
|----------|---------|-----|
| `client.verify()` | `pan`, `gstin`, `rc`, `voterId`, `drivingLicense`, `passport`, `cin`, `udyam`, `panToGstin`, `panToUdyam`, `bankAccount` | `POST /v1/verify/*` |
| `client.services()` | `list`, `get(serviceCode)` | `GET /v1/services` |
| `client.estamp()` | `getSchema`, `listStates`, `listInstruments`, `listArticles`, `calculateDuty`, `addParties` | `/v1/estamp/*` |
| `client.orders()` | `create`, `get`, `getStatus`, `getPayment`, `listDocuments`, `getDocumentDownload` | `/v1/orders/*` |
| `client.payments().intents()` | `create`, `get` | `/v1/payments/intents` |
| `client.usage()` | `summary` | `GET /v1/usage/summary` |
| `client.logs()` | `list`, `iterate`, `stream`, `pages` | `GET /v1/logs` |
| `client.webhooks().events()` | `list`, `replay` | `/v1/webhooks/events` |

Every method has an `...Async` twin returning a `CompletableFuture` (see [Async](#async-and-cancellation)).

```java
import in.stampmitra.sdk.requests.CreateOrderRequest;
import in.stampmitra.sdk.requests.CreatePaymentIntentRequest;
import in.stampmitra.sdk.requests.EstampDutyCalculateRequest;
import in.stampmitra.sdk.requests.EstampPartiesRequest;
import in.stampmitra.sdk.resources.LogsParams;
import java.math.BigDecimal;

// Verification (every call is billed, including valid=false results)
client.verify().pan("ABCDE1234F", "Rahul Kumar");
client.verify().gstin("27AABCT1332L1ZZ");
client.verify().rc("MH12AB1234");
client.verify().voterId("ABC1234567");
client.verify().drivingLicense("MH1420110012345", "1990-01-31");   // IP-allowlisted key required
client.verify().passport("PA1079341954215", "1990-01-31");         // IP-allowlisted key required
client.verify().cin("U74999DL2015PTC284009");
client.verify().udyam("UDYAM-MH-12-1234567");
client.verify().panToGstin("ABCDE1234F");
client.verify().panToUdyam("ABCDE1234F");
client.verify().bankAccount("000123456789", "ICIC0001234", "Rahul Kumar", null);

// Services and e-Stamp reference data
client.services().list();                                   // iterable of Service
client.services().get("estamp.instrument.rent_agreement");
client.estamp().getSchema("estamp.instrument.rent_agreement");
client.estamp().listStates();
client.estamp().listInstruments("MH");
client.estamp().listArticles("rent_agreement");
client.estamp().calculateDuty(EstampDutyCalculateRequest.builder()
        .stateCode("MH").articleCode("5").stampDutyAmount(new BigDecimal("500")).build());
client.estamp().addParties("ord_123", EstampPartiesRequest.builder().customerName("A").partyName("B").build());

// Orders and payments
client.orders().create(CreateOrderRequest.builder()
        .externalOrderId("o1").externalCustomerId("c1").serviceCode("estamp.instrument.rent_agreement").build());
client.orders().get("o1");
client.orders().getStatus("o1");
client.orders().getPayment("o1");
client.orders().listDocuments("o1");
client.orders().getDocumentDownload("o1", "doc_1");
client.payments().intents().create(CreatePaymentIntentRequest.builder()
        .externalOrderId("o1").amountMinor(50000).currency("INR").paymentConnectionId("pc_1").build());
client.payments().intents().get("pi_1");

// Usage, logs, webhook events
client.usage().summary();
client.logs().list(LogsParams.builder().service("pan.verify").status("success").limit(50).build());
client.webhooks().events().list(20, null);
client.webhooks().events().replay("evt_1");
```

Orders are identified by the **`externalOrderId`** that `create` returns (there is no `id`); pass it to `get`,
`getStatus`, `getPayment`, `listDocuments` and `addParties`. Methods that take a request class also accept a plain
`Map<String, Object>` for fields the SDK does not model yet.

Responses are immutable models (`pan.getRegisteredName()`). **Fields the SDK does not know about are never dropped**:
`pan.getRaw()` returns the complete decoded body and `pan.get("someNewField")` reads any field by its JSON name.
HTTP metadata (status, headers, request id, number of attempts) is available from `pan.getResponse()`.
Endpoints whose shape the API does not fix (e-Stamp reference data, duty, parties, webhook events) return
`GenericResponse` (`getRaw()`, `get(key)`, `getItems()`). `GET /v1/services` answers with a bare JSON array, exposed as an
iterable `ServiceList`. `toString()` of a model masks personal data.

## Errors

All errors are unchecked and extend `in.stampmitra.sdk.errors.StampMitraException` (`getStatus()`, `getErrorCode()`,
`getMessage()`, `getRequestId()`, `getField()`, `getBody()`, `getHeaders()`):

| HTTP | Exception |
|------|-----------|
| 400 (and local validation) | `ValidationException` |
| 401 | `AuthenticationException` |
| 402 | `PaymentRequiredException` |
| 403 | `PermissionException` (server message verbatim, e.g. IP allowlist) |
| 404 | `NotFoundException` |
| 409 | `ConflictException` |
| 429 | `RateLimitException` (`getRetryAfterSeconds()` when the header is present) |
| 5xx | `UpstreamException` |
| network / timeout | `ConnectionException` / `RequestTimeoutException` |
| webhook verification | `SignatureVerificationException` |

Other statuses (for example an unexpected 3xx) raise the base `StampMitraException`; non-JSON error bodies still map by
status. Exceptions never contain the API key.

```java
import in.stampmitra.sdk.errors.RateLimitException;
import in.stampmitra.sdk.errors.StampMitraException;

try {
    client.verify().gstin("27AABCT1332L1ZZ");
} catch (RateLimitException e) {
    // back off: e.getRetryAfterSeconds() may be null
} catch (StampMitraException e) {
    System.err.println(e.getRequestId() + ": " + e.getMessage());
}
```

## Retries and idempotency

Default `maxRetries` is 2 (at most 3 attempts) with exponential backoff and full jitter (0.5 s base, 8 s cap);
`Retry-After` is honoured when the server sends it (capped at 60 s). Retried: 429, 502, 503, 504, connection errors and
timeouts. 4xx other than 429 is never retried.

- `GET` is always retry-safe.
- The SDK generates a random UUIDv4 `Idempotency-Key` (from the JDK's `SecureRandom`-backed `UUID.randomUUID()`) for
  **every `POST` except webhook replay** and re-uses the **same key across automatic retries**. The API honours the header on
  every metered call, so a retried verification replays the original result instead of billing twice. Pass your own key with
  `RequestOptions.withIdempotencyKey("...")`.
- A `POST` without a key (webhook replay) is never retried unless you supply one.

```java
import in.stampmitra.sdk.RequestOptions;
import java.time.Duration;

RequestOptions options = RequestOptions.builder()
        .idempotencyKey("order-2026-0001")
        .timeout(Duration.ofSeconds(10))
        .maxRetries(1)
        .header("X-Request-Id", "my-correlation-id")
        .build();
client.orders().create(CreateOrderRequest.builder()
        .externalOrderId("order-2026-0001").externalCustomerId("c1").serviceCode("s").build(), options);
```

Note: the JDK `HttpClient` itself may transparently re-send an idempotent `GET` once when a pooled connection was
closed by the peer; this is invisible to the SDK (`getResponse().getAttempts()` counts SDK attempts).

### Timeouts

`timeout` (default 30 s) applies **per attempt** and covers connecting, waiting for headers and reading the body. A timed-out
call raises `RequestTimeoutException` (a `ConnectionException`) and is retried like any connection error when retry-safe.

## Async and cancellation

Each method has an `...Async` twin (`panAsync`, `createAsync`, `listAsync`, ...) returning a `CompletableFuture`. Calls block a
pool thread while they run (default: a shared pool of daemon threads; pass `executor(...)` to the builder to use your own).
Failures, including validation errors, complete the future exceptionally. `future.cancel(true)` interrupts the call. The
synchronous methods react to `Thread.interrupt()` as well.

```java
import in.stampmitra.sdk.models.GstinVerification;
import in.stampmitra.sdk.models.PanVerification;
import java.util.concurrent.CompletableFuture;

CompletableFuture<PanVerification> pan = client.verify().panAsync("ABCDE1234F");
CompletableFuture<GstinVerification> gstin = client.verify().gstinAsync("27AABCT1332L1ZZ");
String summary = pan.thenCombine(gstin, (p, g) -> p.isValid() + "/" + g.isValid()).join();
```

## Pagination

`client.logs().iterate(params)` returns an `Iterable<LogEntry>` that follows `nextCursor` lazily; `stream(params)` returns a
`java.util.stream.Stream`; `pages(params)` iterates whole pages. Each call starts again from the first page.

```java
import in.stampmitra.sdk.models.LogEntry;
import in.stampmitra.sdk.models.LogPage;
import in.stampmitra.sdk.resources.LogsParams;

LogsParams params = LogsParams.builder().service("pan.verify").limit(100).build();
for (LogEntry entry : client.logs().iterate(params)) {
    System.out.println(entry.getId() + " " + entry.getCreatedAt());
}
LogPage page = client.logs().list(params);   // page.getItems(), page.getNextCursor()
long firstFive = client.logs().stream(params).limit(5).count();
```

## Webhooks

Deliveries carry `X-Webhook-Signature`, `X-Webhook-Timestamp` (unix seconds) and `X-Webhook-Event-Id`. The signature is
`hex(HMAC-SHA256(secret, "{timestamp}.{rawBody}"))` over the body **exactly as received**. Comparison is constant-time,
timestamps more than 300 s (configurable) from now are rejected, an empty secret is rejected, and failures raise
`SignatureVerificationException` from `constructEvent`.

```java
import in.stampmitra.sdk.Webhook;
import in.stampmitra.sdk.models.WebhookEvent;
import in.stampmitra.sdk.models.WebhookEventType;

// rawBody: the request body as received (do not parse and re-serialise it)
WebhookEvent event = Webhook.constructEvent(rawBody, timestamp, signature, secret);
if (event.getType() == WebhookEventType.ORDER_PAYMENT_CONFIRMED) {
    System.out.println(event.getCoreOrderId() + " paid at " + event.getOccurredAt());
}
boolean authentic = Webhook.verifySignature(rawBody, timestamp, signature, secret);   // tolerance 300 s
```

`WebhookEvent` is typed for the eight `order.*` events (`getType()`, `getCoreOrderId()`, `getCoreEventType()`,
`getOccurredAt()`, `getEventId()`); the event's own extra fields are preserved (`event.get("...")`, `event.getRaw()`).
`Webhook.constructEventFromHeaders(rawBody, headers, secret)` reads the three headers from any `Map<String, ? extends
Collection<String>>` (e.g. `com.sun.net.httpserver.Headers`, servlet header maps). Runnable receivers:
[`examples/.../WebhookReceiver.java`](examples/src/main/java/in/stampmitra/examples/WebhookReceiver.java) (JDK
`HttpServer`, no framework) and a [Spring Boot controller](examples/src/spring/java/in/stampmitra/examples/spring/WebhookController.java)
(take the body as `byte[]`, never as a parsed object).

## Validation

Inputs are checked before sending (PAN, GSTIN, IFSC, CIN, Udyam, vehicle number, 9-18 digit account number, passport file
number, driving licence, voter ID, phone, ISO dates, names up to 256 characters): trimmed, upper-cased (the account number is only
trimmed), with a local `ValidationException` (`getField()` names the input) and **no network call or billing**. Separators are
**not** stripped (`MH-12 AB 1234` is rejected, like the API does). Use the validators directly or disable them with
`validateInput(false)`:

```java
import in.stampmitra.sdk.Validators;

String pan = Validators.pan(" abcde1234f ");   // "ABCDE1234F"
```

## Logging

Nothing is logged by default. Pass `logger(line -> ...)` or `debug(true)` (writes to `System.Logger` `in.stampmitra.sdk` at
DEBUG) to get one line per request, response and retry. `Authorization`/`x-api-key` are redacted, identifiers keep their last
four characters and names, dates of birth and addresses are removed. The API key never appears in log lines, exception
messages, stack traces or `toString()` output.

```java
StampMitra debugging = StampMitra.builder()
        .apiKey(System.getenv("STAMPMITRA_API_KEY"))
        .logger(line -> System.err.println("[stampmitra] " + line))
        .build();
```

## Custom HTTP client

The transport is an interface (`in.stampmitra.sdk.http.HttpTransport`). The default `JdkHttpTransport` can wrap your own
`HttpClient` (custom `SSLContext`, authenticated proxy, executor); it must be built with `followRedirects(NEVER)`. To use OkHttp,
Apache HttpClient or Vert.x, implement `HttpTransport`: never follow redirects, return every status as a response, honour the
per-attempt timeout. A complete OkHttp adapter is in
[`OkHttpTransportExample.java`](examples/src/main/java/in/stampmitra/examples/OkHttpTransportExample.java).

```java
import in.stampmitra.sdk.http.JdkHttpTransport;
import java.net.http.HttpClient;

HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
StampMitra custom = StampMitra.builder()
        .apiKey(System.getenv("STAMPMITRA_API_KEY"))
        .transport(new JdkHttpTransport(jdk))
        .build();
```

## Pricing

The SDK is free and MIT-licensed. You pay only for API calls, and only for successful production calls; Sandbox is free.

| Plan | Rate |
|---|---|
| **Sandbox** (test keys, `sk_test_...`) | Free. Calls run against sandbox data and never debit your wallet. |
| **Developer Suite** (live keys, `sk_live_...`) | ₹2 per call: PAN, GSTIN, vehicle RC, Voter ID, Driving Licence, Passport, CIN, Udyam, PAN to GSTIN, PAN to Udyam |
| | ₹4 per call: bank account (penny-drop) |
| | ₹5 per call: each e-Stamp call |
| **White-label / CA fulfilment** | Custom, with volume pricing and SLAs. [Contact us](https://developer.stampmitra.in/contact). |

Production calls are deducted from your API credit wallet (top up under Dashboard, Billing). Rates are shown for convenience and may change; the [pricing page](https://developer.stampmitra.in/pricing) is the source of truth.

## Security

- Use keys server-side only; keep them in environment variables or a secret manager; never commit them.
- TLS verification is never disabled by the SDK. **Redirects are never followed**, so credentials cannot leak to another host;
  a 3xx is reported as an error. `baseUrl` must be `https://` (loopback hosts excepted) and must not carry credentials.
- The key is held in a wrapper that prints `[REDACTED]`; it is scrubbed from every exception message and cause, log line and
  `toString()`.
- Path ids are validated and percent-encoded (empty, `.`, `..` and slashes are rejected); caller headers are checked for
  injection and cannot replace `Authorization`.
- Always verify webhook signatures on the raw body and keep the default 300 s timestamp tolerance.
- Report vulnerabilities as described in [SECURITY.md](SECURITY.md), not in public issues.

## Supported versions

Java 11, 17 and 21 are exercised in CI (`--release 11` bytecode). Only the latest minor release receives fixes while the SDK
is pre-1.0. See [CHANGELOG.md](CHANGELOG.md).

## Development

```bash
mvn -B verify                      # compile (-Xlint:all -Werror), checkstyle, tests, javadoc (no warnings), jacoco
node scripts/generate.mjs --check  # generated models/resources match spec/openapi.json
scripts/run-examples.sh            # run every example against the spec repo's mock server
```

`spec/openapi.json` is a snapshot of the public spec; `SpecCoverageTest` fails if any `METHOD + path` in it has no SDK method
(`mvn -q test -Dtest=SpecCoverageTest`). The conformance suite (`src/test/java/.../conformance`) starts the spec repo's mock
server and exercises all 31 operations; set `STAMPMITRA_SPEC_DIR` to a checkout of `stampmitra-developer-openapi`. See
[CONTRIBUTING.md](CONTRIBUTING.md), [RELEASING.md](RELEASING.md) and [SECURITY.md](SECURITY.md).

Licensed under the [MIT License](LICENSE). Copyright Bani Global Industries LLP (StampMitra).
