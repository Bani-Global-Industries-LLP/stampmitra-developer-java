package in.stampmitra.sdk;

import in.stampmitra.sdk.errors.AuthenticationException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.http.HttpTransport;
import in.stampmitra.sdk.http.JdkHttpTransport;
import in.stampmitra.sdk.internal.RequestExecutor;
import in.stampmitra.sdk.internal.Secret;
import in.stampmitra.sdk.resources.EstampResource;
import in.stampmitra.sdk.resources.LogsResource;
import in.stampmitra.sdk.resources.OrdersResource;
import in.stampmitra.sdk.resources.PaymentsResource;
import in.stampmitra.sdk.resources.ServicesResource;
import in.stampmitra.sdk.resources.UsageResource;
import in.stampmitra.sdk.resources.VerifyResource;
import in.stampmitra.sdk.resources.WebhooksResource;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StampMitra Developer API client. Thread-safe and cheap to share: create one per API key and reuse it.
 *
 * <pre>{@code
 * StampMitra client = StampMitra.builder().apiKey(System.getenv("STAMPMITRA_API_KEY")).build();
 * PanVerification pan = client.verify().pan("ABCDE1234F");
 * }</pre>
 *
 * <p><b>Server-side only:</b> never ship {@code sk_} keys in Android apps, desktop apps or anything a user can unpack.
 */
public final class StampMitra {

    /** SDK version, also sent in the {@code User-Agent} header. */
    public static final String VERSION = "0.1.0";
    /** Production and sandbox base URL (the key prefix {@code sk_test_} or {@code sk_live_} selects the mode). */
    public static final String DEFAULT_BASE_URL = "https://api-b2b.stampmitra.in";
    /** Environment variable read when no API key is passed to the builder. */
    public static final String API_KEY_ENV = "STAMPMITRA_API_KEY";

    private static final Pattern KEY_PATTERN = Pattern.compile("^sk_(test|live)_[A-Za-z0-9_-]{8,}$");
    private static final Pattern CUSTOM_KEY_PATTERN = Pattern.compile("^[\\x21-\\x7e]{8,512}$");

    private final String baseUrl;
    private final String environment;
    private final VerifyResource verify;
    private final ServicesResource services;
    private final EstampResource estamp;
    private final OrdersResource orders;
    private final PaymentsResource payments;
    private final UsageResource usage;
    private final LogsResource logs;
    private final WebhooksResource webhooks;

    private StampMitra(Builder b) {
        String key = b.apiKey;
        if (key == null || key.trim().isEmpty()) {
            String env = System.getenv(API_KEY_ENV);
            key = env == null ? null : env;
        }
        if (key == null || key.trim().isEmpty()) {
            throw new AuthenticationException("No API key provided. Pass apiKey(...) or set " + API_KEY_ENV + ".", null,
                    "MISSING_API_KEY", null, null, null, null, null);
        }
        key = key.trim();
        Matcher m = KEY_PATTERN.matcher(key);
        if (m.matches()) {
            this.environment = m.group(1);
        } else if (b.allowCustomKey && CUSTOM_KEY_PATTERN.matcher(key).matches()) {
            this.environment = "custom";
        } else {
            throw new AuthenticationException("API key must look like sk_test_... or sk_live_... "
                    + "(use allowCustomKey(true) to override).", null, "INVALID_API_KEY_FORMAT", null, null, null, null,
                    null);
        }
        this.baseUrl = checkBaseUrl(b.baseUrl);
        if (b.timeout.isNegative() || b.timeout.isZero()) {
            throw new ValidationException("timeout", "timeout must be positive");
        }
        if (b.maxRetries < 0) {
            throw new ValidationException("maxRetries", "maxRetries must be >= 0");
        }
        Map<String, String> headers = new LinkedHashMap<>(b.defaultHeaders);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            RequestExecutor.checkHeader(e.getKey(), e.getValue());
        }

        Consumer<String> logger = b.logger;
        if (logger == null && b.debug) {
            System.Logger systemLogger = System.getLogger("in.stampmitra.sdk");
            logger = line -> systemLogger.log(System.Logger.Level.DEBUG, line);
        }
        RequestExecutor executor = new RequestExecutor(new RequestExecutor.Config(new Secret(key), baseUrl,
                b.transport != null ? b.transport : new JdkHttpTransport(), b.timeout, b.maxRetries,
                Collections.unmodifiableMap(headers), userAgent(b), logger,
                b.sleeper != null ? b.sleeper : d -> Thread.sleep(d.toMillis()),
                b.executor != null ? b.executor : AsyncPool.INSTANCE));

        this.verify = new VerifyResource(executor, b.validateInput);
        this.services = new ServicesResource(executor, b.validateInput);
        this.estamp = new EstampResource(executor, b.validateInput);
        this.orders = new OrdersResource(executor, b.validateInput);
        this.payments = new PaymentsResource(executor, b.validateInput);
        this.usage = new UsageResource(executor, b.validateInput);
        this.logs = new LogsResource(executor, b.validateInput);
        this.webhooks = new WebhooksResource(executor, b.validateInput);
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
     * Identity verification (PAN, GSTIN, RC, voter id, driving licence, passport, CIN, Udyam, bank account).
     *
     * @return the verify resource
     */
    public VerifyResource verify() {
        return verify;
    }

    /**
     * The service catalogue.
     *
     * @return the services resource
     */
    public ServicesResource services() {
        return services;
    }

    /**
     * e-Stamp Paper reference data and helpers.
     *
     * @return the e-Stamp resource
     */
    public EstampResource estamp() {
        return estamp;
    }

    /**
     * Orders.
     *
     * @return the orders resource
     */
    public OrdersResource orders() {
        return orders;
    }

    /**
     * Payments ({@code payments().intents()}).
     *
     * @return the payments resource
     */
    public PaymentsResource payments() {
        return payments;
    }

    /**
     * API usage summary.
     *
     * @return the usage resource
     */
    public UsageResource usage() {
        return usage;
    }

    /**
     * Request logs with cursor pagination.
     *
     * @return the logs resource
     */
    public LogsResource logs() {
        return logs;
    }

    /**
     * Webhook delivery events ({@code webhooks().events()}).
     *
     * @return the webhooks resource
     */
    public WebhooksResource webhooks() {
        return webhooks;
    }

    /**
     * Whether the key is a sandbox or a production key.
     *
     * @return {@code "test"}, {@code "live"}, or {@code "custom"} when {@code allowCustomKey} accepted another key
     */
    public String environment() {
        return environment;
    }

    /**
     * The base URL requests are sent to.
     *
     * @return the base URL without a trailing slash
     */
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public String toString() {
        return "StampMitra{baseUrl=" + baseUrl + ", environment=" + environment + "}";
    }

    private static String userAgent(Builder b) {
        StringBuilder ua = new StringBuilder("stampmitra-java/").append(VERSION).append(" (Java ")
                .append(System.getProperty("java.version", "unknown")).append(')');
        if (b.appName != null) {
            ua.append(' ').append(b.appName);
            if (b.appVersion != null) {
                ua.append('/').append(b.appVersion);
            }
            if (b.appUrl != null) {
                ua.append(" (").append(b.appUrl).append(')');
            }
        }
        return ua.toString();
    }

    /**
     * The API key travels in the Authorization header, so plain http is only allowed for loopback hosts (mock servers,
     * tests); credentials in the URL are rejected.
     */
    private static String checkBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException e) {
            throw new ValidationException("baseUrl", "baseUrl must be an absolute https URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new ValidationException("baseUrl", "baseUrl must be an absolute https URL");
        }
        if (uri.getRawUserInfo() != null || baseUrl.contains("@")) {
            throw new ValidationException("baseUrl", "baseUrl must not contain credentials");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new ValidationException("baseUrl", "baseUrl must not contain a query string or fragment");
        }
        boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")
                || host.equals("::1");
        if (scheme.equals("http") && !loopback) {
            throw new ValidationException("baseUrl", "baseUrl must use https (http is only allowed for localhost)");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /** Shared daemon thread pool for the {@code *Async} methods. */
    private static final class AsyncPool {
        static final Executor INSTANCE = create();

        private AsyncPool() {
        }

        private static Executor create() {
            AtomicInteger counter = new AtomicInteger();
            ThreadFactory factory = r -> {
                Thread t = new Thread(r, "stampmitra-async-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            };
            return new ThreadPoolExecutor(0, 64, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), factory,
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }
    }

    /** Builder for {@link StampMitra}. */
    public static final class Builder {

        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private HttpTransport transport;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private String appName;
        private String appVersion;
        private String appUrl;
        private Consumer<String> logger;
        private boolean debug;
        private boolean validateInput = true;
        private boolean allowCustomKey;
        private Executor executor;
        private Sleeper sleeper;

        private Builder() {
        }

        /**
         * Sets the API key ({@code sk_test_...} or {@code sk_live_...}). Defaults to the
         * {@value StampMitra#API_KEY_ENV} environment variable.
         *
         * @param value the key
         * @return this builder
         */
        public Builder apiKey(String value) {
            this.apiKey = value;
            return this;
        }

        /**
         * Overrides the base URL. Must be {@code https} (plain {@code http} only for localhost) and must not contain
         * credentials.
         *
         * @param value the base URL
         * @return this builder
         */
        public Builder baseUrl(String value) {
            this.baseUrl = Objects.requireNonNull(value, "baseUrl");
            return this;
        }

        /**
         * Sets the per-attempt timeout (default 30 seconds).
         *
         * @param value a positive duration
         * @return this builder
         */
        public Builder timeout(Duration value) {
            this.timeout = Objects.requireNonNull(value, "timeout");
            return this;
        }

        /**
         * Sets the number of automatic retries (default 2; 0 disables retries).
         *
         * @param value zero or more
         * @return this builder
         */
        public Builder maxRetries(int value) {
            this.maxRetries = value;
            return this;
        }

        /**
         * Replaces the HTTP layer, for example with an OkHttp adapter.
         *
         * @param value the transport
         * @return this builder
         */
        public Builder transport(HttpTransport value) {
            this.transport = Objects.requireNonNull(value, "transport");
            return this;
        }

        /**
         * Adds a header sent with every request ({@code Authorization} and {@code X-Api-Key} cannot be set).
         *
         * @param name  header name
         * @param value header value
         * @return this builder
         */
        public Builder defaultHeader(String name, String value) {
            this.defaultHeaders.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Identifies your application in the {@code User-Agent} header.
         *
         * @param name    application name
         * @param version application version, may be {@code null}
         * @param url     application URL, may be {@code null}
         * @return this builder
         */
        public Builder appInfo(String name, String version, String url) {
            this.appName = Objects.requireNonNull(name, "name");
            this.appVersion = version;
            this.appUrl = url;
            return this;
        }

        /**
         * Receives debug lines for every request, response and retry. Authorization headers and personal data are
         * redacted; nothing is logged unless a logger is set or {@link #debug(boolean)} is enabled.
         *
         * @param value the log sink
         * @return this builder
         */
        public Builder logger(Consumer<String> value) {
            this.logger = value;
            return this;
        }

        /**
         * Sends the (redacted) debug lines to {@link System.Logger} {@code in.stampmitra.sdk} at level DEBUG. Off by
         * default.
         *
         * @param value whether to enable it
         * @return this builder
         */
        public Builder debug(boolean value) {
            this.debug = value;
            return this;
        }

        /**
         * Enables or disables local input validation (default on). Disabling it lets the server reject malformed
         * input, which is still billed.
         *
         * @param value whether to validate
         * @return this builder
         */
        public Builder validateInput(boolean value) {
            this.validateInput = value;
            return this;
        }

        /**
         * Accepts API keys that do not look like {@code sk_test_...} or {@code sk_live_...}.
         *
         * @param value whether to accept them
         * @return this builder
         */
        public Builder allowCustomKey(boolean value) {
            this.allowCustomKey = value;
            return this;
        }

        /**
         * Executor for the {@code *Async} methods (calls block a thread while they run). Defaults to a shared pool of
         * daemon threads.
         *
         * @param value the executor
         * @return this builder
         */
        public Builder executor(Executor value) {
            this.executor = Objects.requireNonNull(value, "executor");
            return this;
        }

        /**
         * Replaces the retry pause, mainly for tests.
         *
         * @param value the sleeper
         * @return this builder
         */
        public Builder sleeper(Sleeper value) {
            this.sleeper = Objects.requireNonNull(value, "sleeper");
            return this;
        }

        /**
         * Builds the client.
         *
         * @return the client
         * @throws AuthenticationException if no usable API key is configured
         * @throws ValidationException     if the base URL or another setting is invalid
         */
        public StampMitra build() {
            return new StampMitra(this);
        }
    }
}
