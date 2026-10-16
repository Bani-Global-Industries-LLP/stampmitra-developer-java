package in.stampmitra.sdk.errors;

/**
 * A webhook delivery could not be verified (bad signature, stale timestamp, missing headers, empty secret) or its
 * payload is not a JSON object. {@link #getErrorCode()} holds a stable reason such as {@code SIGNATURE_MISMATCH}.
 */
public class SignatureVerificationException extends StampMitraException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the error.
     *
     * @param message   human readable message (never contains the secret)
     * @param errorCode stable upper-case reason code
     */
    public SignatureVerificationException(String message, String errorCode) {
        super(message, null, errorCode, null, null, null, null, null);
    }
}
