package in.stampmitra.sdk.errors;

import java.util.List;
import java.util.Map;

/**
 * HTTP 429: too many requests. {@link #getRetryAfterSeconds()} is set only when the server sent a
 * {@code Retry-After} header (the API itself usually does not).
 */
public class RateLimitException extends StampMitraException {

    private static final long serialVersionUID = 1L;

    /** Retry-After in seconds, or null. */
    private final Double retryAfterSeconds;

    /**
     * Creates the error.
     *
     * @param message           human readable message
     * @param status            HTTP status, may be {@code null}
     * @param errorCode         machine readable error code, may be {@code null}
     * @param requestId         server-side request id, may be {@code null}
     * @param body              decoded body, may be {@code null}
     * @param field             offending field, may be {@code null}
     * @param headers           response headers, may be {@code null}
     * @param retryAfterSeconds value of {@code Retry-After} in seconds, or {@code null}
     */
    public RateLimitException(String message, Integer status, String errorCode, String requestId, Object body,
                              String field, Map<String, List<String>> headers, Double retryAfterSeconds) {
        super(message, status, errorCode, requestId, body, field, headers, null);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Seconds the server asked the client to wait.
     *
     * @return the {@code Retry-After} value in seconds, or {@code null} when the header was absent
     */
    public Double getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
