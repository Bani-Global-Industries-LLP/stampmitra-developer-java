package in.stampmitra.sdk;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * HTTP metadata of a successful response, available from every model through
 * {@link in.stampmitra.sdk.models.Model#getResponse()}.
 */
public final class ResponseInfo {

    private final int status;
    private final Map<String, List<String>> headers;
    private final String requestId;
    private final int attempts;

    /**
     * Creates the metadata.
     *
     * @param status    HTTP status code
     * @param headers   response headers
     * @param requestId value of the {@code X-Request-Id} header, may be {@code null}
     * @param attempts  number of HTTP attempts it took (1 when nothing was retried)
     */
    public ResponseInfo(int status, Map<String, List<String>> headers, String requestId, int attempts) {
        this.status = status;
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(headers);
        this.headers = Collections.unmodifiableMap(copy);
        this.requestId = requestId;
        this.attempts = attempts;
    }

    /**
     * HTTP status code (any 2xx; POSTs usually answer 201).
     *
     * @return the status
     */
    public int getStatus() {
        return status;
    }

    /**
     * All response headers, matched case-insensitively.
     *
     * @return an unmodifiable header map
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * First value of a header.
     *
     * @param name header name, case-insensitive
     * @return the value, or {@code null} when absent
     */
    public String getHeader(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Server request id from the {@code X-Request-Id} header, when the server sent one.
     *
     * @return the request id, or {@code null}
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Number of HTTP attempts, including automatic retries.
     *
     * @return the attempt count, at least 1
     */
    public int getAttempts() {
        return attempts;
    }

    @Override
    public String toString() {
        return "ResponseInfo{status=" + status + ", requestId=" + requestId + ", attempts=" + attempts + "}";
    }
}
