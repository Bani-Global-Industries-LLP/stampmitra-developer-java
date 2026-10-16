package in.stampmitra.sdk.http;

import java.io.IOException;

/** Thrown by an {@link HttpTransport} adapter when the per-attempt timeout elapsed. */
public class TransportTimeoutException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what timed out
     */
    public TransportTimeoutException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message what timed out
     * @param cause   the underlying timeout
     */
    public TransportTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
