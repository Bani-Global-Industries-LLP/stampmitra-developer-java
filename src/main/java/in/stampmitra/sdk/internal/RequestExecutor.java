package in.stampmitra.sdk.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import in.stampmitra.sdk.RequestOptions;
import in.stampmitra.sdk.ResponseInfo;
import in.stampmitra.sdk.Sleeper;
import in.stampmitra.sdk.errors.AuthenticationException;
import in.stampmitra.sdk.errors.ConflictException;
import in.stampmitra.sdk.errors.ConnectionException;
import in.stampmitra.sdk.errors.NotFoundException;
import in.stampmitra.sdk.errors.PaymentRequiredException;
import in.stampmitra.sdk.errors.PermissionException;
import in.stampmitra.sdk.errors.RateLimitException;
import in.stampmitra.sdk.errors.RequestTimeoutException;
import in.stampmitra.sdk.errors.StampMitraException;
import in.stampmitra.sdk.errors.UpstreamException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.http.HttpTransport;
import in.stampmitra.sdk.http.TransportRequest;
import in.stampmitra.sdk.http.TransportResponse;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Builds requests, retries safely and maps failures to typed exceptions. Internal: use the resource classes.
 *
 * <p>Retry rules:
 *
 * <ul>
 *   <li>GET is always retry-safe.
 *   <li>POST is retried only when it carries an {@code Idempotency-Key}. The SDK adds one to every POST except
 *       webhook replay, and re-uses the same key for every attempt.
 *   <li>Retried: 429, 502, 503, 504, connection errors and timeouts.
 * </ul>
 */
public final class RequestExecutor {

    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[\\x21-\\x7e]{1,255}$");
    private static final double BACKOFF_BASE_SECONDS = 0.5;
    private static final double BACKOFF_CAP_SECONDS = 8.0;
    private static final double RETRY_AFTER_CAP_SECONDS = 60.0;
    private static final int MAX_ERROR_BODY_CHARS = 4096;

    /** Immutable settings of an executor. Internal. */
    public static final class Config {
        private final Secret apiKey;
        private final String baseUrl;
        private final HttpTransport transport;
        private final Duration timeout;
        private final int maxRetries;
        private final Map<String, String> defaultHeaders;
        private final String userAgent;
        private final Consumer<String> logger;
        private final Sleeper sleeper;
        private final Executor asyncExecutor;

        /**
         * Creates the settings.
         *
         * @param apiKey         the credential
         * @param baseUrl        validated base URL without trailing slash
         * @param transport      HTTP layer
         * @param timeout        default per-attempt timeout
         * @param maxRetries     default retry count
         * @param defaultHeaders validated headers sent with every request
         * @param userAgent      User-Agent value
         * @param logger         debug sink, or {@code null}
         * @param sleeper        retry pause
         * @param asyncExecutor  executor for {@code *Async} calls
         */
        public Config(Secret apiKey, String baseUrl, HttpTransport transport, Duration timeout, int maxRetries,
                      Map<String, String> defaultHeaders, String userAgent, Consumer<String> logger, Sleeper sleeper,
                      Executor asyncExecutor) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.transport = transport;
            this.timeout = timeout;
            this.maxRetries = maxRetries;
            this.defaultHeaders = defaultHeaders;
            this.userAgent = userAgent;
            this.logger = logger;
            this.sleeper = sleeper;
            this.asyncExecutor = asyncExecutor;
        }
    }

    private final Config config;

    /**
     * Creates an executor.
     *
     * @param config its settings
     */
    public RequestExecutor(Config config) {
        this.config = config;
    }

    /**
     * Runs a blocking call on the client's async executor.
     *
     * @param call the call
     * @param <T>  result type
     * @return a cancellable future
     */
    public <T> CompletableFuture<T> async(Callable<T> call) {
        return Async.run(config.asyncExecutor, call);
    }

    /**
     * Sends a request and builds the typed result.
     *
     * @param op               the operation (supplies the HTTP method)
     * @param path             the concrete, already encoded request path
     * @param query            query parameters (null values are skipped)
     * @param body             request body ({@code Map}) or {@code null}
     * @param options          per-request options, may be {@code null}
     * @param autoIdempotency  generate an {@code Idempotency-Key} when the caller passed none
     * @param factory          builds the model from the decoded body
     * @param <T>              model type
     * @return the model
     */
    public <T> T execute(Operation op, String path, Map<String, String> query, Object body, RequestOptions options,
                         boolean autoIdempotency, ResponseFactory<T> factory) {
        RequestOptions opts = options == null ? RequestOptions.none() : options;
        String idempotencyKey = opts.getIdempotencyKey();
        if (idempotencyKey != null && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ValidationException("idempotencyKey", "idempotencyKey must be 1-255 printable ASCII characters");
        }
        if (idempotencyKey == null && autoIdempotency) {
            idempotencyKey = UUID.randomUUID().toString(); // java.util.UUID uses a SecureRandom (CSPRNG)
        }
        boolean isGet = "GET".equals(op.method());
        boolean retrySafe = isGet || idempotencyKey != null;
        int maxRetries = opts.getMaxRetries() != null ? opts.getMaxRetries() : config.maxRetries;
        Duration timeout = opts.getTimeout() != null ? opts.getTimeout() : config.timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new ValidationException("timeout", "timeout must be positive");
        }
        byte[] payload = null;
        if (!isGet) {
            try {
                payload = Json.toBytes(body == null ? Collections.emptyMap() : body);
            } catch (JsonProcessingException e) {
                throw new ValidationException("body", "request body cannot be serialised to JSON");
            }
        }
        Map<String, String> requestHeaders = headersFor(opts, idempotencyKey);
        URI uri = buildUri(path, query);

        int attempt = 0;
        while (true) {
            attempt++;
            TransportRequest request = new TransportRequest(op.method(), uri, requestHeaders, payload, timeout);
            logRequest(request, attempt);

            TransportResponse response;
            try {
                response = config.transport.send(request);
            } catch (IOException e) {
                StampMitraException error = connectionError(e, timeout);
                log("transport error attempt=" + attempt + " " + error.getClass().getSimpleName() + ": "
                        + error.getMessage());
                boolean interrupted = e instanceof InterruptedIOException && !(e instanceof SocketTimeoutException);
                if (retrySafe && !interrupted && attempt <= maxRetries) {
                    pause(backoff(attempt), error);
                    continue;
                }
                throw error;
            } catch (RuntimeException e) {
                throw new StampMitraException(config.apiKey.scrub("Transport failure: " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage())), null, "TRANSPORT_ERROR", null, null,
                        null, null, sanitize(e));
            }

            int status = response.getStatus();
            String text = new String(response.getBody(), StandardCharsets.UTF_8);
            logResponse(status, text, attempt);

            if (status >= 200 && status < 300) {
                Map<String, Object> decoded = decodeSuccess(text, response);
                ResponseInfo info = new ResponseInfo(status, response.getHeaders(),
                        first(response.getHeaders(), "x-request-id"), attempt);
                return factory.create(decoded, info);
            }

            StampMitraException error = mapError(status, text, response.getHeaders());
            if (retrySafe && attempt <= maxRetries && isRetryableStatus(status)) {
                Double retryAfter = error instanceof RateLimitException
                        ? ((RateLimitException) error).getRetryAfterSeconds()
                        : retryAfter(response.getHeaders());
                Duration delay = retryAfter != null
                        ? seconds(Math.min(retryAfter, RETRY_AFTER_CAP_SECONDS))
                        : backoff(attempt);
                pause(delay, error);
                continue;
            }
            throw error;
        }
    }

    /**
     * Full-jitter exponential backoff for the given attempt (1 = wait before the first retry).
     *
     * @param attempt the attempt that just failed, starting at 1
     * @return a delay between zero and {@code min(8s, 0.5s * 2^(attempt-1))}
     */
    static Duration backoff(int attempt) {
        double ceiling = Math.min(BACKOFF_CAP_SECONDS, BACKOFF_BASE_SECONDS * Math.pow(2, attempt - 1));
        return seconds(ThreadLocalRandom.current().nextDouble() * ceiling);
    }

    private static Duration seconds(double s) {
        return Duration.ofNanos((long) (s * 1_000_000_000d));
    }

    private static boolean isRetryableStatus(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private void pause(Duration delay, StampMitraException lastError) {
        log("retrying in " + delay.toMillis() + " ms after " + lastError.getClass().getSimpleName());
        try {
            config.sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Interrupted while waiting to retry", null, "INTERRUPTED", null, null, null,
                    null, e);
        }
    }

    private Map<String, String> headersFor(RequestOptions opts, String idempotencyKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + config.apiKey.reveal());
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", config.userAgent);
        headers.putAll(config.defaultHeaders);
        for (Map.Entry<String, String> e : opts.getHeaders().entrySet()) {
            checkHeader(e.getKey(), e.getValue());
            removeIgnoreCase(headers, e.getKey());
            headers.put(e.getKey(), e.getValue());
        }
        if (idempotencyKey != null) {
            removeIgnoreCase(headers, "Idempotency-Key");
            headers.put("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private static void removeIgnoreCase(Map<String, String> headers, String name) {
        headers.keySet().removeIf(k -> k.equalsIgnoreCase(name));
    }

    /**
     * Validates a caller supplied header.
     *
     * @param name  header name
     * @param value header value
     * @throws ValidationException if the header is reserved, malformed or could be used for header injection
     */
    public static void checkHeader(String name, String value) {
        if (!HEADER_NAME.matcher(name).matches()) {
            throw new ValidationException("headers", "invalid header name");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("authorization") || lower.equals("x-api-key") || lower.equals("host")
                || lower.equals("content-length") || lower.equals("proxy-authorization")) {
            throw new ValidationException("headers", "header " + name + " is managed by the SDK and cannot be set");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7f) {
                throw new ValidationException("headers", "header " + name + " contains control characters");
            }
        }
    }

    private URI buildUri(String path, Map<String, String> query) {
        StringBuilder url = new StringBuilder(config.baseUrl).append(path);
        if (query != null) {
            char sep = '?';
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                url.append(sep).append(encode(e.getKey())).append('=').append(encode(e.getValue()));
                sep = '&';
            }
        }
        return URI.create(url.toString());
    }

    private static String encode(String value) {
        StringBuilder b = new StringBuilder();
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int c = raw & 0xff;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) {
                b.append((char) c);
            } else {
                b.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return b.toString();
    }

    private StampMitraException connectionError(IOException e, Duration timeout) {
        boolean timedOut = e instanceof HttpTimeoutException || e instanceof SocketTimeoutException
                || e instanceof in.stampmitra.sdk.http.TransportTimeoutException;
        String detail = config.apiKey.scrub(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        if (timedOut) {
            return new RequestTimeoutException("Request timed out after " + timeout + ": " + detail, null, "TIMEOUT",
                    null, null, null, null, sanitize(e));
        }
        return new ConnectionException("Network error: " + detail, null, "CONNECTION_ERROR", null, null, null, null,
                sanitize(e));
    }

    /**
     * Keeps the API key out of stack traces: a cause whose message (or any nested cause message) contains the key is
     * replaced by a copy with a scrubbed message and the original stack trace.
     */
    private Throwable sanitize(Throwable cause) {
        boolean leaks = false;
        for (Throwable t = cause; t != null && !leaks; t = t.getCause()) {
            leaks = t.getMessage() != null && t.getMessage().contains(config.apiKey.reveal());
        }
        if (!leaks) {
            return cause;
        }
        IOException copy = new IOException(cause.getClass().getName() + ": " + config.apiKey.scrub(cause.getMessage()));
        copy.setStackTrace(cause.getStackTrace());
        return copy;
    }

    private Map<String, Object> decodeSuccess(String text, TransportResponse response) {
        if (text.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Object decoded;
        try {
            decoded = Json.parse(text);
        } catch (JsonProcessingException e) {
            throw new StampMitraException("The API returned a success status with a non-JSON body",
                    response.getStatus(), "INVALID_RESPONSE", first(response.getHeaders(), "x-request-id"),
                    truncate(text), null, response.getHeaders(), null);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (decoded instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) decoded).entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        } else if (decoded instanceof List) {
            out.put("items", decoded);
        } else {
            out.put("value", decoded);
        }
        return out;
    }

    private StampMitraException mapError(int status, String text, Map<String, List<String>> headers) {
        Object body = text.isEmpty() ? null : truncate(text);
        String message = null;
        String code = null;
        String field = null;
        String requestId = null;
        try {
            Object parsed = text.trim().isEmpty() ? null : Json.parse(text);
            if (parsed instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) parsed;
                body = Json.freeze(map);
                Object err = map.get("error");
                if (err instanceof Map) {
                    Map<?, ?> e = (Map<?, ?>) err;
                    message = str(e.get("message"));
                    code = str(e.get("code"));
                    field = str(e.get("field"));
                    requestId = str(e.get("requestId"));
                } else {
                    message = err instanceof String ? (String) err : str(map.get("message"));
                }
            }
        } catch (JsonProcessingException e) {
            // keep the raw text as the body
        }
        if (message == null || message.isEmpty()) {
            message = status >= 300 && status < 400
                    ? "Unexpected redirect (HTTP " + status + "); the SDK never follows redirects"
                    : "HTTP " + status;
        }
        message = config.apiKey.scrub(message);
        if (requestId == null) {
            requestId = first(headers, "x-request-id");
        }
        if (body instanceof String) {
            body = config.apiKey.scrub((String) body);
        }
        Integer s = status;
        if (status == 400) {
            return new ValidationException(message, s, code, requestId, body, field, headers, null);
        } else if (status == 401) {
            return new AuthenticationException(message, s, code, requestId, body, field, headers, null);
        } else if (status == 402) {
            return new PaymentRequiredException(message, s, code, requestId, body, field, headers, null);
        } else if (status == 403) {
            return new PermissionException(message, s, code, requestId, body, field, headers, null);
        } else if (status == 404) {
            return new NotFoundException(message, s, code, requestId, body, field, headers, null);
        } else if (status == 409) {
            return new ConflictException(message, s, code, requestId, body, field, headers, null);
        } else if (status == 429) {
            return new RateLimitException(message, s, code, requestId, body, field, headers, retryAfter(headers));
        } else if (status >= 500) {
            return new UpstreamException(message, s, code, requestId, body, field, headers, null);
        }
        return new StampMitraException(message, s, code, requestId, body, field, headers, null);
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_BODY_CHARS ? text : text.substring(0, MAX_ERROR_BODY_CHARS);
    }

    private static String first(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    /**
     * Parses a {@code Retry-After} header (delay seconds or HTTP date).
     *
     * @param headers response headers
     * @return seconds to wait, or {@code null} when absent or unparsable
     */
    static Double retryAfter(Map<String, List<String>> headers) {
        String value = first(headers, "retry-after");
        if (value == null) {
            return null;
        }
        value = value.trim();
        try {
            double seconds = Double.parseDouble(value);
            return Double.isNaN(seconds) || Double.isInfinite(seconds) ? null : Math.max(0.0, seconds);
        } catch (NumberFormatException e) {
            // fall through to the HTTP-date form
        }
        try {
            ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            double delta = (when.toInstant().toEpochMilli() - System.currentTimeMillis()) / 1000.0;
            return Math.max(0.0, delta);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void logRequest(TransportRequest request, int attempt) {
        if (config.logger == null) {
            return;
        }
        byte[] body = request.getBody();
        log("-> " + request.getMethod() + " " + request.getUri() + " attempt=" + attempt + " headers="
                + Redactor.headers(request.getHeaders()) + " body="
                + (body == null ? "" : Redactor.body(new String(body, StandardCharsets.UTF_8))));
    }

    private void logResponse(int status, String text, int attempt) {
        if (config.logger != null) {
            log("<- " + status + " attempt=" + attempt + " body=" + Redactor.body(text));
        }
    }

    private void log(String line) {
        if (config.logger != null) {
            config.logger.accept(config.apiKey.scrub(line));
        }
    }

    @Override
    public String toString() {
        return "RequestExecutor{baseUrl=" + config.baseUrl + "}";
    }
}
