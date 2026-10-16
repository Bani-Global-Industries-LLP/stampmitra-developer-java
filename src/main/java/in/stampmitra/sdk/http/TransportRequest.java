package in.stampmitra.sdk.http;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One HTTP request handed to an {@link HttpTransport}. Immutable. {@link #toString()} redacts credential headers.
 */
public final class TransportRequest {

    private final String method;
    private final URI uri;
    private final Map<String, String> headers;
    private final byte[] body;
    private final Duration timeout;

    /**
     * Creates a request.
     *
     * @param method  HTTP method in upper case
     * @param uri     absolute request URI
     * @param headers request headers (one value per name)
     * @param body    request body, or {@code null} for none
     * @param timeout total time allowed for this attempt
     */
    public TransportRequest(String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout) {
        this.method = Objects.requireNonNull(method, "method");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(headers, "headers")));
        this.body = body == null ? null : body.clone();
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * HTTP method.
     *
     * @return the upper-case method
     */
    public String getMethod() {
        return method;
    }

    /**
     * Absolute URI including the query string.
     *
     * @return the URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Request headers, including {@code Authorization}.
     *
     * @return an unmodifiable, ordered header map
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Request body bytes (UTF-8 JSON).
     *
     * @return a copy of the body, or {@code null} when the request has none
     */
    public byte[] getBody() {
        return body == null ? null : body.clone();
    }

    /**
     * Total time allowed for this attempt.
     *
     * @return the timeout
     */
    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public String toString() {
        return "TransportRequest{" + method + " " + uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath()
                + ", headers=" + headers.keySet() + ", bodyBytes=" + (body == null ? 0 : body.length) + "}";
    }
}
