package in.stampmitra.sdk.models;

import in.stampmitra.sdk.ResponseInfo;
import in.stampmitra.sdk.internal.Json;
import in.stampmitra.sdk.internal.Redactor;
import in.stampmitra.sdk.internal.ResponseFactory;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base class of all response models. Models are immutable views over the decoded JSON body.
 *
 * <p>Unknown fields are never dropped: {@link #getRaw()} returns the complete body and {@link #get(String)} reads any
 * field by its JSON name, so new API fields are available before the SDK adds a typed getter. HTTP metadata (status,
 * headers, request id, attempts) is available from {@link #getResponse()}.
 *
 * <p>{@link #toString()} masks personal data (identifiers keep their last four characters, names are removed).
 */
public abstract class Model {

    private final Map<String, Object> raw;
    private final ResponseInfo response;

    /**
     * Creates a model over a decoded JSON object.
     *
     * @param raw      the decoded body (copied; may be {@code null} for an empty body)
     * @param response HTTP metadata, or {@code null} for models that did not come from a response
     */
    protected Model(Map<String, Object> raw, ResponseInfo response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> frozen = raw == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) Json.freeze(raw);
        this.raw = frozen;
        this.response = response;
    }

    /**
     * The complete decoded JSON body, including fields this SDK version does not know.
     *
     * @return an unmodifiable, deeply immutable map
     */
    public final Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * Reads any field by its JSON name.
     *
     * @param key the JSON property name
     * @return the decoded value (String, Number, Boolean, Map, List) or {@code null}
     */
    public final Object get(String key) {
        return raw.get(key);
    }

    /**
     * HTTP metadata of the response this model was built from.
     *
     * @return the metadata, or {@code null} if the model was constructed manually
     */
    public final ResponseInfo getResponse() {
        return response;
    }

    /**
     * The complete body as JSON text (not masked).
     *
     * @return the JSON
     */
    public final String toJson() {
        return Json.toJson(raw);
    }

    @Override
    public final boolean equals(Object o) {
        return o != null && o.getClass() == getClass() && ((Model) o).raw.equals(raw);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass().getName(), raw);
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + Redactor.mask(raw);
    }

    final String string(String key) {
        Object v = raw.get(key);
        return v == null ? null : String.valueOf(v);
    }

    final boolean bool(String key) {
        return Boolean.TRUE.equals(raw.get(key));
    }

    final Boolean boolOrNull(String key) {
        Object v = raw.get(key);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    final Long longValue(String key) {
        Object v = raw.get(key);
        return v instanceof Number ? Long.valueOf(((Number) v).longValue()) : null;
    }

    final BigDecimal decimal(String key) {
        Object v = raw.get(key);
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        return v instanceof Number ? new BigDecimal(v.toString()) : null;
    }

    final Instant instant(String key) {
        Object v = raw.get(key);
        if (!(v instanceof String)) {
            return null;
        }
        try {
            return OffsetDateTime.parse((String) v).toInstant();
        } catch (DateTimeException e) {
            try {
                return Instant.parse((String) v);
            } catch (DateTimeException e2) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    final Map<String, Object> map(String key) {
        Object v = raw.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> mapList(String key) {
        Object v = raw.get(key);
        return v instanceof List ? (List<Map<String, Object>>) v : Collections.<Map<String, Object>>emptyList();
    }

    final <T> T model(String key, ResponseFactory<T> factory) {
        Map<String, Object> m = map(key);
        return m == null ? null : factory.create(m, null);
    }

    final <T> List<T> models(String key, ResponseFactory<T> factory) {
        Object v = raw.get(key);
        List<T> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object item : (List<?>) v) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) item;
                    out.add(factory.create(m, null));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }
}
