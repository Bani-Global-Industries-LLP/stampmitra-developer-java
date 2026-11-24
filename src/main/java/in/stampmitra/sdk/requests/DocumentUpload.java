package in.stampmitra.sdk.requests;

import in.stampmitra.sdk.errors.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * The PAN card file for {@code POST /v1/estamp/orders/{orderId}/documents} (multipart part {@code file}): a PDF, JPEG
 * or PNG of at most 10 MB. The content is held in memory, so automatic retries re-send identical bytes. Immutable.
 *
 * <pre>{@code
 * client.estamp().orders().uploadDocument(orderId, DocumentUpload.of(Paths.get("pan.pdf")));
 * }</pre>
 */
public final class DocumentUpload {

    /** Largest accepted file, in bytes (10 MB). */
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    private final String filename;
    private final String contentType;
    private final byte[] content;

    private DocumentUpload(String filename, String contentType, byte[] content) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
    }

    /**
     * Reads a file; the content type is derived from the extension ({@code .pdf}, {@code .jpg}, {@code .jpeg},
     * {@code .png}), otherwise {@code application/octet-stream}.
     *
     * @param path the file
     * @return the upload
     * @throws ValidationException if the file cannot be read
     */
    public static DocumentUpload of(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            Path name = path.getFileName();
            String filename = name == null ? "file" : name.toString();
            if (Files.size(path) > MAX_BYTES) {
                throw new ValidationException("file", "file must be at most 10 MB");
            }
            return of(filename, Files.readAllBytes(path));
        } catch (IOException e) {
            throw new ValidationException("file", "file cannot be read: " + e.getClass().getSimpleName());
        }
    }

    /**
     * From bytes; the content type is derived from the file name extension.
     *
     * @param filename the file name, for example {@code pan.pdf}
     * @param content  the bytes
     * @return the upload
     */
    public static DocumentUpload of(String filename, byte[] content) {
        return of(filename, guessContentType(filename), content);
    }

    /**
     * From bytes with an explicit content type.
     *
     * @param filename    the file name
     * @param contentType the media type, {@code application/pdf}, {@code image/jpeg} or {@code image/png}
     * @param content     the bytes
     * @return the upload
     */
    public static DocumentUpload of(String filename, String contentType, byte[] content) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new ValidationException("file", "file name must not be empty");
        }
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(content, "content");
        return new DocumentUpload(filename, contentType, content.clone());
    }

    private static String guessContentType(String filename) {
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) {
            return "application/pdf";
        } else if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (n.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    /**
     * The file name sent in the part.
     *
     * @return the name
     */
    public String getFilename() {
        return filename;
    }

    /**
     * The declared media type.
     *
     * @return the type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * The file bytes.
     *
     * @return a copy
     */
    public byte[] getContent() {
        return content.clone();
    }

    /**
     * Checks the documented limits: not empty and at most 10 MB.
     *
     * @throws ValidationException if a limit is violated
     */
    public void validate() {
        if (content.length == 0) {
            throw new ValidationException("file", "file must not be empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ValidationException("file", "file must be at most 10 MB");
        }
    }

    @Override
    public String toString() {
        return "DocumentUpload{" + filename + ", " + contentType + ", " + content.length + " bytes}";
    }
}
