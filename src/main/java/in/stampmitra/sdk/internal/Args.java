package in.stampmitra.sdk.internal;

import in.stampmitra.sdk.errors.ValidationException;

/** Argument checks shared by the resource classes. Internal. */
public final class Args {

    private Args() {
    }

    /**
     * Rejects {@code null} (and blank strings) for a required argument.
     *
     * @param value the argument
     * @param name  its JSON field name
     * @param <T>   argument type
     * @return the value
     * @throws ValidationException if the value is missing
     */
    public static <T> T required(T value, String name) {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            throw new ValidationException(name, name + " is required");
        }
        return value;
    }
}
