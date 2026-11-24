package in.stampmitra.sdk.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@code multipart/form-data} request body held in memory (RFC 7578). Internal: use the resource methods.
 *
 * <p>The body is rendered once; automatic retries re-send the same bytes, so a retried upload carries the identical
 * file and the identical {@code Idempotency-Key}.
 */
public final class Multipart {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CRLF = "\r\n";

    private final String boundary;
    private final List<Object[]> parts = new ArrayList<>();

    private Multipart(String boundary) {
        this.boundary = boundary;
    }

    /**
     * Starts an empty body with a fresh random boundary.
     *
     * @return the builder
     */
    public static Multipart create() {
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        StringBuilder b = new StringBuilder("----StampMitraBoundary");
        for (byte x : random) {
            b.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        }
        return new Multipart(b.toString());
    }

    /**
     * Adds a text part.
     *
     * @param name  the field name
     * @param value the value, sent as UTF-8
     * @return this body
     */
    public Multipart text(String name, String value) {
        parts.add(new Object[] {checkName(name), null, null, Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8)});
        return this;
    }

    /**
     * Adds a file part.
     *
     * @param name        the field name
     * @param filename    the file name as the receiver should see it
     * @param contentType the media type of the content
     * @param content     the file bytes
     * @return this body
     */
    public Multipart file(String name, String filename, String contentType, byte[] content) {
        parts.add(new Object[] {checkName(name), Objects.requireNonNull(filename, "filename"),
                Objects.requireNonNull(contentType, "contentType"), Objects.requireNonNull(content, "content")});
        return this;
    }

    /**
     * The {@code Content-Type} header value, including the boundary.
     *
     * @return {@code multipart/form-data; boundary=...}
     */
    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    /**
     * Renders the body.
     *
     * @return the bytes to send
     */
    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object[] p : parts) {
            write(out, "--" + boundary + CRLF);
            String disposition = "Content-Disposition: form-data; name=\"" + p[0] + "\"";
            if (p[1] != null) {
                disposition += "; " + filenameParams((String) p[1]);
            }
            write(out, disposition + CRLF);
            if (p[2] != null) {
                write(out, "Content-Type: " + headerSafe((String) p[2]) + CRLF);
            }
            write(out, CRLF);
            byte[] data = (byte[]) p[3];
            out.write(data, 0, data.length);
            write(out, CRLF);
        }
        write(out, "--" + boundary + "--" + CRLF);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        out.write(b, 0, b.length);
    }

    private static String checkName(String name) {
        Objects.requireNonNull(name, "name");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x21 || c > 0x7e || c == '"' || c == '\\') {
                throw new IllegalArgumentException("invalid multipart field name");
            }
        }
        return name;
    }

    /** Strips CR/LF so a value can never end the header line. */
    private static String headerSafe(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    /** {@code filename="..."} with an ASCII fallback, plus RFC 5987 {@code filename*} when the name is not ASCII. */
    private static String filenameParams(String filename) {
        StringBuilder ascii = new StringBuilder();
        boolean exact = true;
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            if (c < 0x20 || c > 0x7e || c == '"' || c == '\\') {
                ascii.append('_');
                exact = false;
            } else {
                ascii.append(c);
            }
        }
        String out = "filename=\"" + ascii + "\"";
        if (!exact) {
            StringBuilder enc = new StringBuilder();
            for (byte raw : filename.getBytes(StandardCharsets.UTF_8)) {
                int c = raw & 0xff;
                boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                        || c == '-' || c == '.' || c == '_' || c == '~';
                if (unreserved) {
                    enc.append((char) c);
                } else {
                    enc.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                            .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
                }
            }
            out += "; filename*=UTF-8''" + enc;
        }
        return out;
    }
}
