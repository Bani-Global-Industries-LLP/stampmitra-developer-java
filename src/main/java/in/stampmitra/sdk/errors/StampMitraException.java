package in.stampmitra.sdk.errors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base class of every error raised by the SDK.
 *
 * <p>For API errors the HTTP status, the machine readable error code, the server-side request id and the decoded
 * response body are available through the getters. Errors raised locally (client-side validation, network failures)
 * have no status.
 *
 * <p>Instances never contain the API key: messages are scrubbed before the exception is created.
 */
public class StampMitraException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** HTTP status, or null for local errors. */
    private final Integer status;
    /** Machine readable error code. */
    private final String errorCode;
    /** Server-side request id. */
    private final String requestId;
    /** Offending input field. */
    private final String field;
    /** Decoded response body (not serialized). */
    private final transient Object body;
    /** Response headers (not serialized). */
    private final transient Map<String, List<String>> headers;

    /**
     * Creates an error that did not come from an HTTP response.
     *
     * @param message human readable message
     */
    public StampMitraException(String message) {
        this(message, null, null, null, null, null, null, null);
    }

    /**
     * Creates an error with full detail.
     *
     * @param message   human readable message
     * @param status    HTTP status, or {@code null} for local errors
     * @param errorCode machine readable error code, may be {@code null}
     * @param requestId server-side request id, may be {@code null}
     * @param body      decoded JSON body (a {@code Map}) or raw text for non-JSON bodies, may be {@code null}
     * @param field     offending input field, may be {@code null}
     * @param headers   response headers, may be {@code null}
     * @param cause     underlying cause, may be {@code null}
     */
    public StampMitraException(String message, Integer status, String errorCode, String requestId, Object body,
                               String field, Map<String, List<String>> headers, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.requestId = requestId;
        this.body = body;
        this.field = field;
        this.headers = headers == null ? Collections.<String, List<String>>emptyMap() : headers;
    }

    /**
     * HTTP status code.
     *
     * @return the status, or {@code null} when the error did not come from a response
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Machine readable error code from the response, for example {@code VALIDATION_ERROR}.
     *
     * @return the code, or {@code null}
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Server-side request id. Quote it when contacting support.
     *
     * @return the request id, or {@code null}
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * The offending input field for validation errors, when known.
     *
     * @return the field name, or {@code null}
     */
    public String getField() {
        return field;
    }

    /**
     * The decoded error body: a {@code Map} for JSON objects, the raw text for anything else.
     *
     * @return the body, or {@code null}
     */
    public Object getBody() {
        return body;
    }

    /**
     * Response headers (empty for local errors).
     *
     * @return an unmodifiable header map
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }
}
