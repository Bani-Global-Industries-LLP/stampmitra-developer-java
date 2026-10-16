package in.stampmitra.sdk;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-request overrides accepted by every SDK method. Immutable; build with {@link #builder()}.
 *
 * <pre>{@code
 * client.orders().create(order, RequestOptions.builder().idempotencyKey("order-42").timeout(Duration.ofSeconds(5)).build());
 * }</pre>
 *
 * <p>Cancellation: the synchronous methods react to {@link Thread#interrupt()}; the {@code *Async} methods return a
 * {@link java.util.concurrent.CompletableFuture} whose {@code cancel(true)} interrupts the call.
 */
public final class RequestOptions {

    private static final RequestOptions NONE = new RequestOptions(null, null, null, Collections.emptyMap());

    private final Duration timeout;
    private final Integer maxRetries;
    private final String idempotencyKey;
    private final Map<String, String> headers;

    private RequestOptions(Duration timeout, Integer maxRetries, String idempotencyKey, Map<String, String> headers) {
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.idempotencyKey = idempotencyKey;
        this.headers = headers;
    }

    /**
     * Options that change nothing.
     *
     * @return the shared empty options
     */
    public static RequestOptions none() {
        return NONE;
    }

    /**
     * Starts a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Shortcut for options that only set an idempotency key.
     *
     * @param key the key to send as {@code Idempotency-Key}
     * @return options carrying the key
     */
    public static RequestOptions withIdempotencyKey(String key) {
        return builder().idempotencyKey(key).build();
    }

    /**
     * Per-attempt timeout override.
     *
     * @return the timeout, or {@code null} to use the client default
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Retry count override.
     *
     * @return the maximum number of retries, or {@code null} to use the client default
     */
    public Integer getMaxRetries() {
        return maxRetries;
    }

    /**
     * Caller supplied idempotency key.
     *
     * @return the key, or {@code null} to let the SDK generate one for POST requests
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Extra headers for this request only.
     *
     * @return an unmodifiable header map
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String toString() {
        return "RequestOptions{timeout=" + timeout + ", maxRetries=" + maxRetries + ", idempotencyKey="
                + (idempotencyKey == null ? "null" : "set") + ", headers=" + headers.keySet() + "}";
    }

    /** Builder for {@link RequestOptions}. */
    public static final class Builder {

        private Duration timeout;
        private Integer maxRetries;
        private String idempotencyKey;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Overrides the per-attempt timeout for this call.
         *
         * @param value a positive duration
         * @return this builder
         */
        public Builder timeout(Duration value) {
            this.timeout = Objects.requireNonNull(value, "timeout");
            return this;
        }

        /**
         * Overrides the number of automatic retries for this call (0 disables retries).
         *
         * @param value zero or more
         * @return this builder
         */
        public Builder maxRetries(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = value;
            return this;
        }

        /**
         * Sets the {@code Idempotency-Key}. The same key is reused across automatic retries.
         *
         * @param value an opaque key of your choice, for example your own order id
         * @return this builder
         */
        public Builder idempotencyKey(String value) {
            this.idempotencyKey = Objects.requireNonNull(value, "idempotencyKey");
            return this;
        }

        /**
         * Adds a header to this request only.
         *
         * @param name  header name
         * @param value header value
         * @return this builder
         */
        public Builder header(String name, String value) {
            headers.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Builds the immutable options.
         *
         * @return the options
         */
        public RequestOptions build() {
            return new RequestOptions(timeout, maxRetries, idempotencyKey,
                    Collections.unmodifiableMap(new LinkedHashMap<>(headers)));
        }
    }
}
