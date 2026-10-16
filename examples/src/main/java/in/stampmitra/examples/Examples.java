package in.stampmitra.examples;

import in.stampmitra.sdk.StampMitra;

/** Shared setup for the examples. */
final class Examples {

    private Examples() {
    }

    /**
     * A client configured from the environment: {@code STAMPMITRA_API_KEY} (required) and, to run against a local
     * mock server, {@code STAMPMITRA_BASE_URL} (optional).
     */
    static StampMitra client() {
        StampMitra.Builder builder = StampMitra.builder().apiKey(System.getenv("STAMPMITRA_API_KEY"));
        String baseUrl = System.getenv("STAMPMITRA_BASE_URL");
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
