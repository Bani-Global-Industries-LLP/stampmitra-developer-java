package in.stampmitra.sdk.requests;

import in.stampmitra.sdk.errors.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One party to an e-Stamp instrument. The API accepts a party as a bare name string or as an object; this type
 * serialises to whichever form you built:
 *
 * <pre>{@code
 * EstampParty.of("Jane Doe");                                   // "Jane Doe"
 * EstampParty.builder("Jane Doe").address("12 MG Road, Pune")
 *         .gender(EstampParty.Gender.FEMALE).build();           // {"name":..., "address":..., "gender":"FEMALE"}
 * }</pre>
 *
 * <p>Use the object form whenever the state wants an address or a father's or husband's name (see the state
 * requirements route). Immutable.
 */
public final class EstampParty {

    /** The party's gender. */
    public enum Gender {
        /** Male. */
        MALE,
        /** Female. */
        FEMALE,
        /** Other. */
        OTHER
    }

    private static final int MAX_NAME = 150;

    private final Map<String, Object> fields;
    private final boolean bare;

    private EstampParty(Map<String, Object> fields, boolean bare) {
        this.fields = fields;
        this.bare = bare;
    }

    /**
     * A party given as a bare name; it is sent as a JSON string.
     *
     * @param name the full name, 1 to 150 characters
     * @return the party
     * @throws ValidationException if the name is empty or too long
     */
    public static EstampParty of(String name) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", checkText("name", name, 1, MAX_NAME));
        return new EstampParty(f, true);
    }

    /**
     * Starts the object form of a party.
     *
     * @param name the full name as on the identity document, 1 to 150 characters
     * @return a builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * The party's name.
     *
     * @return the name
     */
    public String getName() {
        return (String) fields.get("name");
    }

    /**
     * The wire form: a {@code String} for a bare-name party, otherwise an unmodifiable {@code Map}.
     *
     * @return what is placed in the JSON body
     */
    public Object toWire() {
        return bare ? fields.get("name") : java.util.Collections.unmodifiableMap(fields);
    }

    private static String checkText(String field, String value, int min, int max) {
        if (value == null || value.trim().length() < min) {
            throw new ValidationException(field, field + " is required");
        }
        if (value.length() > max) {
            throw new ValidationException(field, field + " must be at most " + max + " characters");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EstampParty && ((EstampParty) o).bare == bare && ((EstampParty) o).fields.equals(fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, bare);
    }

    @Override
    public String toString() {
        return "EstampParty" + fields.keySet();
    }

    /** Builder for the object form of {@link EstampParty}. */
    public static final class Builder {

        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(String name) {
            fields.put("name", checkText("name", name, 1, MAX_NAME));
        }

        /**
         * Sets the address (required by states that report {@code firstPartyAddressRequired}).
         *
         * @param value up to 500 characters
         * @return this builder
         */
        public Builder address(String value) {
            return put("address", value, 500);
        }

        /**
         * Sets the phone number.
         *
         * @param value up to 20 characters
         * @return this builder
         */
        public Builder phone(String value) {
            return put("phone", value, 20);
        }

        /**
         * Sets the father's or husband's name (required by states that report {@code fatherHusbandNameRequired}).
         *
         * @param value up to 150 characters
         * @return this builder
         */
        public Builder fatherHusbandName(String value) {
            return put("fatherHusbandName", value, MAX_NAME);
        }

        /**
         * Sets the gender.
         *
         * @param value the gender, or {@code null} to clear it
         * @return this builder
         */
        public Builder gender(Gender value) {
            if (value == null) {
                fields.remove("gender");
            } else {
                fields.put("gender", value.name());
            }
            return this;
        }

        private Builder put(String key, String value, int max) {
            if (value == null) {
                fields.remove(key);
            } else {
                fields.put(key, checkText(key, value, 0, max));
            }
            return this;
        }

        /**
         * Builds the party.
         *
         * @return the immutable party
         */
        public EstampParty build() {
            return new EstampParty(new LinkedHashMap<>(fields), false);
        }
    }
}
