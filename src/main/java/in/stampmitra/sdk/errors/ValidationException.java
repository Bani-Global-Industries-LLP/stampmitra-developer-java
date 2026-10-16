package in.stampmitra.sdk.errors;

import java.util.List;
import java.util.Map;

/**
 * HTTP 400, or a client-side validation failure raised before any request was sent (no network call, nothing billed).
 * {@link #getField()} names the offending input when known.
 */
public class ValidationException extends StampMitraException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a local validation error for one input field.
     *
     * @param field   the offending field
     * @param message what is wrong with it
     */
    public ValidationException(String field, String message) {
        super(message, null, "VALIDATION_ERROR", null, null, field, null, null);
    }

    /**
     * Creates the error from an API response.
     *
     * @param message   human readable message
     * @param status    HTTP status, may be {@code null}
     * @param errorCode machine readable error code, may be {@code null}
     * @param requestId server-side request id, may be {@code null}
     * @param body      decoded body, may be {@code null}
     * @param field     offending field, may be {@code null}
     * @param headers   response headers, may be {@code null}
     * @param cause     underlying cause, may be {@code null}
     */
    public ValidationException(String message, Integer status, String errorCode, String requestId, Object body,
                               String field, Map<String, List<String>> headers, Throwable cause) {
        super(message, status, errorCode, requestId, body, field, headers, cause);
    }
}
