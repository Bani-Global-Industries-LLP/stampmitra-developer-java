package in.stampmitra.sdk.errors;

import java.util.Objects;

/**
 * One finding about one input, from {@code error.details.errors[]} of a rejected e-Stamp order body. Branch on
 * {@link #getCode()} (for example {@code REQUIRED}, {@code INVALID_UNIT}, {@code MIXED_STATE}) and show
 * {@link #getMessage()}.
 */
public final class FieldIssue {

    private final String field;
    private final String code;
    private final String message;
    private final Integer index;

    /**
     * Creates a finding.
     *
     * @param field   path of the offending input, for example {@code stamps[0].firstParty.name}; may be {@code null}
     * @param code    stable machine code
     * @param message human readable text
     * @param index   zero-based stamp line index, or {@code null}
     */
    public FieldIssue(String field, String code, String message, Integer index) {
        this.field = field;
        this.code = code;
        this.message = message;
        this.index = index;
    }

    /**
     * Path of the offending input in the request body.
     *
     * @return the path, empty or {@code null} when the body was not a JSON object
     */
    public String getField() {
        return field;
    }

    /**
     * Stable machine code.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * Human readable text.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Zero-based index of the stamp line when the finding is inside {@code stamps[i]}.
     *
     * @return the index, or {@code null}
     */
    public Integer getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldIssue)) {
            return false;
        }
        FieldIssue f = (FieldIssue) o;
        return Objects.equals(field, f.field) && Objects.equals(code, f.code) && Objects.equals(message, f.message)
                && Objects.equals(index, f.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, code, message, index);
    }

    @Override
    public String toString() {
        return "FieldIssue{field=" + field + ", code=" + code + ", index=" + index + "}";
    }
}
