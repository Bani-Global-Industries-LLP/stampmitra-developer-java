package in.stampmitra.sdk.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared JSON configuration. Internal: not part of the public API. */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private Json() {
    }

    /**
     * The configured mapper (unknown properties ignored, big decimals, ISO dates).
     *
     * @return the shared mapper
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Parses JSON into plain maps, lists and scalars.
     *
     * @param text the JSON text
     * @return a {@code Map}, {@code List}, scalar or {@code null}
     * @throws JsonProcessingException if the text is not valid JSON
     */
    public static Object parse(String text) throws JsonProcessingException {
        return MAPPER.readValue(text, Object.class);
    }

    /**
     * Serialises a value to UTF-8 JSON bytes.
     *
     * @param value the value
     * @return the JSON bytes
     * @throws JsonProcessingException if the value cannot be serialised
     */
    public static byte[] toBytes(Object value) throws JsonProcessingException {
        return MAPPER.writeValueAsBytes(value);
    }

    /**
     * Serialises a value to a JSON string.
     *
     * @param value the value
     * @return the JSON text
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * Deep, unmodifiable copy of a parsed JSON structure.
     *
     * @param value a parsed value
     * @return an immutable copy
     */
    public static Object freeze(Object value) {
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                out.put(String.valueOf(e.getKey()), freeze(e.getValue()));
            }
            return Collections.unmodifiableMap(out);
        }
        if (value instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object o : (List<?>) value) {
                out.add(freeze(o));
            }
            return Collections.unmodifiableList(out);
        }
        return value;
    }
}
