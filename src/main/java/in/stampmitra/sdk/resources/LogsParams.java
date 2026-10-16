package in.stampmitra.sdk.resources;

import in.stampmitra.sdk.errors.ValidationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Filters for {@code GET /v1/logs}. Immutable; build with {@link #builder()}. */
public final class LogsParams {

    private static final LogsParams EMPTY = new LogsParams(null, null, null, null, null, null);

    private final String from;
    private final String to;
    private final String service;
    private final String status;
    private final Integer limit;
    private final String cursor;

    private LogsParams(String from, String to, String service, String status, Integer limit, String cursor) {
        this.from = from;
        this.to = to;
        this.service = service;
        this.status = status;
        this.limit = limit;
        this.cursor = cursor;
    }

    /**
     * No filters (server defaults: page size 50).
     *
     * @return empty parameters
     */
    public static LogsParams none() {
        return EMPTY;
    }

    /**
     * Starts a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Copy of these parameters with another cursor.
     *
     * @param newCursor the cursor, or {@code null} for the first page
     * @return the new parameters
     */
    public LogsParams withCursor(String newCursor) {
        return new LogsParams(from, to, service, status, limit, newCursor);
    }

    /**
     * The cursor, if any.
     *
     * @return the cursor or {@code null}
     */
    public String getCursor() {
        return cursor;
    }

    /**
     * Checks the documented constraints ({@code status} is success or failure, {@code limit} 1 to 200).
     *
     * @throws ValidationException if a constraint is violated
     */
    public void validate() {
        if (status != null && !status.equals("success") && !status.equals("failure")) {
            throw new ValidationException("status", "status must be 'success' or 'failure'");
        }
        if (limit != null && (limit < 1 || limit > 200)) {
            throw new ValidationException("limit", "limit must be between 1 and 200");
        }
    }

    /**
     * The query string parameters (unset filters are omitted).
     *
     * @return an ordered map of query parameters
     */
    public Map<String, String> toQuery() {
        Map<String, String> q = new LinkedHashMap<>();
        put(q, "from", from);
        put(q, "to", to);
        put(q, "service", service);
        put(q, "status", status);
        put(q, "limit", limit == null ? null : String.valueOf(limit));
        put(q, "cursor", cursor);
        return q;
    }

    private static void put(Map<String, String> q, String key, String value) {
        if (value != null) {
            q.put(key, value);
        }
    }

    @Override
    public String toString() {
        return "LogsParams" + toQuery().keySet();
    }

    /** Builder for {@link LogsParams}. */
    public static final class Builder {

        private String from;
        private String to;
        private String service;
        private String status;
        private Integer limit;
        private String cursor;

        private Builder() {
        }

        /**
         * Lower bound (inclusive).
         *
         * @param value the instant
         * @return this builder
         */
        public Builder from(Instant value) {
            this.from = Objects.requireNonNull(value, "from").toString();
            return this;
        }

        /**
         * Lower bound (inclusive) as an ISO date or date-time string.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder from(String value) {
            this.from = value;
            return this;
        }

        /**
         * Upper bound (exclusive).
         *
         * @param value the instant
         * @return this builder
         */
        public Builder to(Instant value) {
            this.to = Objects.requireNonNull(value, "to").toString();
            return this;
        }

        /**
         * Upper bound (exclusive) as an ISO date or date-time string.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder to(String value) {
            this.to = value;
            return this;
        }

        /**
         * Restricts the logs to one service code.
         *
         * @param value the service code
         * @return this builder
         */
        public Builder service(String value) {
            this.service = value;
            return this;
        }

        /**
         * Restricts the logs by outcome.
         *
         * @param value {@code success} or {@code failure}
         * @return this builder
         */
        public Builder status(String value) {
            this.status = value;
            return this;
        }

        /**
         * Page size, 1 to 200 (server default 50).
         *
         * @param value the page size
         * @return this builder
         */
        public Builder limit(int value) {
            this.limit = value;
            return this;
        }

        /**
         * Starts at an opaque cursor from a previous page.
         *
         * @param value the cursor
         * @return this builder
         */
        public Builder cursor(String value) {
            this.cursor = value;
            return this;
        }

        /**
         * Builds the immutable parameters.
         *
         * @return the parameters
         */
        public LogsParams build() {
            return new LogsParams(from, to, service, status, limit, cursor);
        }
    }
}
