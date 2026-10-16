package in.stampmitra.sdk.internal;

import in.stampmitra.sdk.ResponseInfo;
import java.util.Map;

/**
 * Builds a typed model from a decoded response body (model constructors fit this shape). Internal.
 *
 * @param <T> the model type
 */
@FunctionalInterface
public interface ResponseFactory<T> {

    /**
     * Creates the model.
     *
     * @param raw  decoded JSON object (an array body is wrapped as {@code {"items": [...]}})
     * @param info HTTP metadata
     * @return the model
     */
    T create(Map<String, Object> raw, ResponseInfo info);
}
