package in.stampmitra.sdk.internal;

import in.stampmitra.sdk.errors.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** One API operation: an HTTP method plus a path template such as {@code /v1/orders/{id}}. Internal. */
public final class Operation {

    private final String method;
    private final String template;

    private Operation(String method, String template) {
        this.method = method;
        this.template = template;
    }

    /**
     * A GET operation.
     *
     * @param template the path template
     * @return the operation
     */
    public static Operation get(String template) {
        return new Operation("GET", template);
    }

    /**
     * A POST operation.
     *
     * @param template the path template
     * @return the operation
     */
    public static Operation post(String template) {
        return new Operation("POST", template);
    }

    /**
     * HTTP method.
     *
     * @return the method
     */
    public String method() {
        return method;
    }

    /**
     * Path template.
     *
     * @return the template
     */
    public String template() {
        return template;
    }

    /**
     * Fills the template placeholders in order, validating and percent-encoding each value.
     *
     * @param values one value per placeholder, in order of appearance
     * @return the concrete request path
     * @throws ValidationException if a value is empty, a dot segment, or contains a slash or control character
     */
    public String render(String... values) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int pos = 0;
        while (true) {
            int open = template.indexOf('{', pos);
            if (open < 0) {
                out.append(template, pos, template.length());
                break;
            }
            int close = template.indexOf('}', open);
            out.append(template, pos, open);
            String name = template.substring(open + 1, close);
            if (i >= values.length) {
                throw new IllegalStateException("missing path parameter " + name);
            }
            out.append(segment(name, values[i++]));
            pos = close + 1;
        }
        return out.toString();
    }

    /**
     * Validates and percent-encodes one path segment.
     *
     * @param name  parameter name used in errors
     * @param value the raw value
     * @return the encoded segment
     * @throws ValidationException if the value cannot be used safely as one path segment
     */
    public static String segment(String name, String value) {
        Objects.requireNonNull(name, "name");
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(name, name + " must not be empty");
        }
        if (value.equals(".") || value.equals("..")) {
            throw new ValidationException(name, name + " must not be a dot segment");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || c < 0x20 || c == 0x7f) {
                throw new ValidationException(name, name + " must not contain slashes or control characters");
            }
        }
        StringBuilder b = new StringBuilder();
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int c = raw & 0xff;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) {
                b.append((char) c);
            } else {
                b.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return method + " " + template;
    }
}
