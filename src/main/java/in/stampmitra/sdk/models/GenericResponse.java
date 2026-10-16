package in.stampmitra.sdk.models;

import in.stampmitra.sdk.ResponseInfo;
import java.util.List;
import java.util.Map;

/**
 * Response of endpoints whose JSON shape the API does not fix (e-Stamp reference data, duty calculation, parties,
 * webhook event list and replay). Use {@link #getRaw()}, {@link #get(String)} or {@link #getItems()}.
 */
public final class GenericResponse extends Model {

    /**
     * Creates a model without HTTP metadata.
     *
     * @param raw the decoded JSON object (an array body appears under {@code "items"})
     */
    public GenericResponse(Map<String, Object> raw) {
        this(raw, null);
    }

    /**
     * Creates a model.
     *
     * @param raw      the decoded JSON object (an array body appears under {@code "items"})
     * @param response HTTP metadata, may be {@code null}
     */
    public GenericResponse(Map<String, Object> raw, ResponseInfo response) {
        super(raw, response);
    }

    /**
     * The elements when the API answered with a JSON array (or an object with an {@code items} array).
     *
     * @return the elements, empty when there are none
     */
    public List<Object> getItems() {
        Object v = get("items");
        if (v instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) v;
            return items;
        }
        return java.util.Collections.emptyList();
    }
}
