package in.stampmitra.sdk.errors;

import java.util.List;
import java.util.Map;

/** HTTP 409: the request conflicts with the current state, for example an idempotency key reused with a different body. */
public class ConflictException extends StampMitraException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the error.
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
    public ConflictException(String message, Integer status, String errorCode, String requestId, Object body, String field,
              Map<String, List<String>> headers, Throwable cause) {
        super(message, status, errorCode, requestId, body, field, headers, cause);
    }
}
