package in.stampmitra.sdk.internal;

import java.util.Objects;

/** Holds a credential and refuses to print it. Internal. */
public final class Secret {

    private final String value;

    /**
     * Wraps a secret.
     *
     * @param value the secret text
     */
    public Secret(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /**
     * The secret text. Only call this where it is sent on the wire.
     *
     * @return the secret
     */
    public String reveal() {
        return value;
    }

    /**
     * Replaces every occurrence of the secret in a text, so that it cannot leak through messages.
     *
     * @param text any text, may be {@code null}
     * @return the text with the secret replaced by {@code [REDACTED]}
     */
    public String scrub(String text) {
        if (text == null || value.isEmpty()) {
            return text;
        }
        return text.replace(value, "[REDACTED]");
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Secret && ((Secret) o).value.equals(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash("Secret");
    }
}
