package in.stampmitra.sdk.requests;

import in.stampmitra.sdk.errors.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Body of {@code POST /v1/estamp/orders/validate} and {@code POST /v1/estamp/orders}: an optional {@code reference}
 * and 1 to 5000 stamp lines that all share one state. Immutable; build with {@link #builder()}. Methods that take
 * this type also accept a plain {@code Map}.
 */
public final class EstampOrderRequest {

    /** Maximum number of stamp lines in one order. */
    public static final int MAX_STAMPS = 5000;
    /** Maximum {@code quantity} of one stamp line. */
    public static final int MAX_QUANTITY = 1000;

    private final Map<String, Object> fields;

    private EstampOrderRequest(Map<String, Object> fields) {
        this.fields = Collections.unmodifiableMap(fields);
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
     * The JSON body as a map.
     *
     * @return an unmodifiable map
     */
    public Map<String, Object> toMap() {
        return fields;
    }

    @Override
    public String toString() {
        return "EstampOrderRequest" + fields.keySet();
    }

    /**
     * Checks the documented constraints of an order body: 1 to 5000 lines, one state, required line fields,
     * {@code quantity} 1 to 1000, positive {@code denomination}, an {@code https} {@code panCardImage}, party names.
     *
     * @param body the body to check
     * @throws ValidationException if a constraint is violated; {@code getField()} is the path, for example
     *                             {@code stamps[0].articleCode}
     */
    public static void validate(Map<String, Object> body) {
        Object stamps = body.get("stamps");
        if (!(stamps instanceof List) || ((List<?>) stamps).isEmpty()) {
            throw new ValidationException("stamps", "stamps must hold at least one stamp line");
        }
        List<?> lines = (List<?>) stamps;
        if (lines.size() > MAX_STAMPS) {
            throw new ValidationException("stamps", "stamps must hold at most " + MAX_STAMPS + " stamp lines");
        }
        String state = null;
        for (int i = 0; i < lines.size(); i++) {
            String p = "stamps[" + i + "]";
            if (!(lines.get(i) instanceof Map)) {
                throw new ValidationException(p, p + " must be an object");
            }
            Map<?, ?> line = (Map<?, ?>) lines.get(i);
            for (String req : new String[] {"state", "articleCode"}) {
                if (!(line.get(req) instanceof String) || ((String) line.get(req)).trim().isEmpty()) {
                    throw new ValidationException(p + "." + req, p + "." + req + " is required");
                }
            }
            String lineState = ((String) line.get("state")).trim().toUpperCase(Locale.ROOT);
            if (state == null) {
                state = lineState;
            } else if (!state.equals(lineState)) {
                throw new ValidationException(p + ".state", "one order covers exactly one state (MIXED_STATE)");
            }
            if (!(line.get("denomination") instanceof Number) || new BigDecimal(line.get("denomination").toString()).signum() <= 0) {
                throw new ValidationException(p + ".denomination", p + ".denomination must be greater than 0");
            }
            checkParty(p + ".firstParty", line.get("firstParty"), true);
            checkParty(p + ".secondParty", line.get("secondParty"), false);
            Object quantity = line.get("quantity");
            if (quantity != null && (!(quantity instanceof Number) || ((Number) quantity).intValue() < 1
                    || ((Number) quantity).intValue() > MAX_QUANTITY)) {
                throw new ValidationException(p + ".quantity", p + ".quantity must be 1 to " + MAX_QUANTITY);
            }
            Object pan = line.get("panCardImage");
            if (pan != null && (!(pan instanceof String) || !((String) pan).startsWith("https://")
                    || ((String) pan).length() > 2048)) {
                throw new ValidationException(p + ".panCardImage", p + ".panCardImage must be an https:// link of at most 2048 characters");
            }
        }
    }

    private static void checkParty(String field, Object party, boolean required) {
        if (party == null) {
            if (required) {
                throw new ValidationException(field, field + " is required");
            }
            return;
        }
        Object name = party instanceof Map ? ((Map<?, ?>) party).get("name") : party;
        if (!(name instanceof String) || ((String) name).trim().isEmpty() || ((String) name).length() > 150) {
            throw new ValidationException(party instanceof Map ? field + ".name" : field,
                    "a party needs a name of 1 to 150 characters (a name string, or an object with name)");
        }
    }

    /** Builder for {@link EstampOrderRequest}. */
    public static final class Builder {

        private String reference;
        private final List<Map<String, Object>> stamps = new ArrayList<>();

        private Builder() {
        }

        /**
         * Your own reference for the whole order (not the {@code Idempotency-Key}).
         *
         * @param value the reference
         * @return this builder
         */
        public Builder reference(String value) {
            this.reference = value;
            return this;
        }

        /**
         * Adds a stamp line.
         *
         * @param line the line
         * @return this builder
         */
        public Builder addStamp(EstampStampLine line) {
            stamps.add(java.util.Objects.requireNonNull(line, "line").toMap());
            return this;
        }

        /**
         * Replaces all stamp lines.
         *
         * @param lines the lines
         * @return this builder
         */
        public Builder stamps(List<EstampStampLine> lines) {
            stamps.clear();
            for (EstampStampLine l : lines) {
                addStamp(l);
            }
            return this;
        }

        /**
         * Builds the request. Constraints are checked when it is sent.
         *
         * @return the immutable request
         */
        public EstampOrderRequest build() {
            Map<String, Object> f = new LinkedHashMap<>();
            if (reference != null) {
                f.put("reference", reference);
            }
            f.put("stamps", Collections.unmodifiableList(new ArrayList<>(stamps)));
            return new EstampOrderRequest(f);
        }
    }
}
