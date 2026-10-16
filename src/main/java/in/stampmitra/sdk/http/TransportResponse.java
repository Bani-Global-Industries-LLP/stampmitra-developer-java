package in.stampmitra.sdk.http;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One HTTP response produced by an {@link HttpTransport}. Immutable. */
public final class TransportResponse {

    private final int status;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    /**
     * Creates a response.
     *
     * @param status  HTTP status code
     * @param headers response headers (names are matched case-insensitively)
     * @param body    complete response body, may be {@code null} or empty
     */
    public TransportResponse(int status, Map<String, List<String>> headers, byte[] body) {
        this.status = status;
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> e : Objects.requireNonNull(headers, "headers").entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                copy.put(e.getKey(), Collections.unmodifiableList(new java.util.ArrayList<>(e.getValue())));
            }
        }
        this.headers = Collections.unmodifiableMap(copy);
        this.body = body == null ? new byte[0] : body.clone();
    }

    /**
     * HTTP status code.
     *
     * @return the status
     */
    public int getStatus() {
        return status;
    }

    /**
     * Response headers with case-insensitive names.
     *
     * @return an unmodifiable header map
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Complete response body.
     *
     * @return a copy of the body bytes (never {@code null})
     */
    public byte[] getBody() {
        return body.clone();
    }

    @Override
    public String toString() {
        return "TransportResponse{status=" + status + ", bodyBytes=" + body.length + "}";
    }
}
