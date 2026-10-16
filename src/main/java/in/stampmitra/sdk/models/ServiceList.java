package in.stampmitra.sdk.models;

import in.stampmitra.sdk.ResponseInfo;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Result of {@code GET /v1/services}, which the API answers with a bare JSON array. The model is iterable over its
 * {@link Service} entries.
 */
public final class ServiceList extends Model implements Iterable<Service> {

    /**
     * Creates a model without HTTP metadata.
     *
     * @param raw the decoded body; the array is under {@code "items"}
     */
    public ServiceList(Map<String, Object> raw) {
        this(raw, null);
    }

    /**
     * Creates a model.
     *
     * @param raw      the decoded body; the array is under {@code "items"}
     * @param response HTTP metadata, may be {@code null}
     */
    public ServiceList(Map<String, Object> raw, ResponseInfo response) {
        super(raw, response);
    }

    /**
     * All services visible to the API key.
     *
     * @return an unmodifiable list
     */
    public List<Service> getServices() {
        return models("items", Service::new);
    }

    /**
     * Number of services.
     *
     * @return the count
     */
    public int size() {
        return getServices().size();
    }

    @Override
    public Iterator<Service> iterator() {
        return getServices().iterator();
    }
}
