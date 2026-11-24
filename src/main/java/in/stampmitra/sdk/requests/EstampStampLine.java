package in.stampmitra.sdk.requests;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One stamp line of an e-Stamp order. Required: {@code state}, {@code articleCode}, {@code denomination} and
 * {@code firstParty}. Unknown properties are rejected by the API, so only documented fields are modelled. Immutable;
 * build with {@link #builder()}.
 *
 * <pre>{@code
 * EstampStampLine.builder().state("MH").articleCode("ART1").denomination(1000)
 *         .firstParty(EstampParty.builder("Jane Doe").address("12 MG Road, Pune").build())
 *         .secondParty("John Smith").build();
 * }</pre>
 */
public final class EstampStampLine {

    /** Stamp category. */
    public enum Category {
        /** {@code e_stamp} (the default). */
        E_STAMP("e_stamp"),
        /** {@code traditional_stamp}. */
        TRADITIONAL_STAMP("traditional_stamp");

        private final String wire;

        Category(String wire) {
            this.wire = wire;
        }

        /**
         * The value sent to the API.
         *
         * @return the wire value
         */
        public String wireName() {
            return wire;
        }
    }

    private final Map<String, Object> fields;

    private EstampStampLine(Map<String, Object> fields) {
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
     * The JSON object of this line.
     *
     * @return an unmodifiable map (parties and the property area already in wire form)
     */
    public Map<String, Object> toMap() {
        return fields;
    }

    @Override
    public String toString() {
        return "EstampStampLine" + fields.keySet();
    }

    /** Builder for {@link EstampStampLine}. */
    public static final class Builder {

        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder put(String key, Object value) {
            if (value == null) {
                fields.remove(key);
            } else {
                fields.put(key, value);
            }
            return this;
        }

        /**
         * Your own reference for this line, echoed back on the created order's stamps.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder clientReference(String value) {
            return put("clientReference", value);
        }

        /**
         * State code from the states route (one order covers exactly one state).
         *
         * @param value for example {@code MH}
         * @return this builder
         */
        public Builder state(String value) {
            return put("state", value);
        }

        /**
         * Stamp category (default {@code e_stamp}).
         *
         * @param value the category
         * @return this builder
         */
        public Builder category(Category value) {
            return put("category", value == null ? null : value.wireName());
        }

        /**
         * Article code of the instrument, scoped to the state.
         *
         * @param value for example {@code ART1}
         * @return this builder
         */
        public Builder articleCode(String value) {
            return put("articleCode", value);
        }

        /**
         * Requested duty denomination in rupees, greater than 0. The authoritative duty is computed by StampMitra.
         *
         * @param value the denomination
         * @return this builder
         */
        public Builder denomination(BigDecimal value) {
            return put("denomination", value);
        }

        /**
         * Requested duty denomination in rupees.
         *
         * @param value the denomination
         * @return this builder
         */
        public Builder denomination(double value) {
            return denomination(BigDecimal.valueOf(value));
        }

        /**
         * The party paying the duty: that party's name, {@code FIRST_PARTY} or {@code SECOND_PARTY}.
         *
         * @param value the payer
         * @return this builder
         */
        public Builder stampDutyPayer(String value) {
            return put("stampDutyPayer", value);
        }

        /**
         * The payer's phone number.
         *
         * @param value the number
         * @return this builder
         */
        public Builder payerPhone(String value) {
            return put("payerPhone", value);
        }

        /**
         * The payer's gender.
         *
         * @param value the gender
         * @return this builder
         */
        public Builder payerGender(EstampParty.Gender value) {
            return put("payerGender", value == null ? null : value.name());
        }

        /**
         * The first party.
         *
         * @param value a party
         * @return this builder
         */
        public Builder firstParty(EstampParty value) {
            return put("firstParty", value == null ? null : value.toWire());
        }

        /**
         * The first party as a bare name.
         *
         * @param name the name
         * @return this builder
         */
        public Builder firstParty(String name) {
            return firstParty(name == null ? null : EstampParty.of(name));
        }

        /**
         * The second party, when the article is not single-party.
         *
         * @param value a party
         * @return this builder
         */
        public Builder secondParty(EstampParty value) {
            return put("secondParty", value == null ? null : value.toWire());
        }

        /**
         * The second party as a bare name.
         *
         * @param name the name
         * @return this builder
         */
        public Builder secondParty(String name) {
            return secondParty(name == null ? null : EstampParty.of(name));
        }

        /**
         * Purpose or nature of the instrument.
         *
         * @param value the purpose
         * @return this builder
         */
        public Builder purpose(String value) {
            return put("purpose", value);
        }

        /**
         * District code, when the state requires one.
         *
         * @param value the code
         * @return this builder
         */
        public Builder district(String value) {
            return put("district", value);
        }

        /**
         * Sub-registrar office value, when the state requires one.
         *
         * @param value the {@code value} of the SRO entry
         * @return this builder
         */
        public Builder sro(String value) {
            return put("sro", value);
        }

        /**
         * Cess amount in rupees, when it applies.
         *
         * @param value the amount
         * @return this builder
         */
        public Builder cessAmount(BigDecimal value) {
            return put("cessAmount", value);
        }

        /**
         * Property area, when the state's {@code propertyAreaRequired} flag is true.
         *
         * @param value the area
         * @return this builder
         */
        public Builder propertyArea(EstampPropertyArea value) {
            return put("propertyArea", value == null ? null : value.toWire());
        }

        /**
         * https link to the stamp-duty payer's PAN card. Optional and advisory: it never blocks an order.
         *
         * @param value an {@code https://} URL of at most 2048 characters
         * @return this builder
         */
        public Builder panCardImage(String value) {
            return put("panCardImage", value);
        }

        /**
         * Your own cost-centre tag.
         *
         * @param value the tag
         * @return this builder
         */
        public Builder costCentre(String value) {
            return put("costCentre", value);
        }

        /**
         * Your own business-unit tag.
         *
         * @param value the tag
         * @return this builder
         */
        public Builder businessUnit(String value) {
            return put("businessUnit", value);
        }

        /**
         * Reference to a related or linked document.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder linkedDocRef(String value) {
            return put("linkedDocRef", value);
        }

        /**
         * Number of identical stamps for this line (1 to 1000, default 1).
         *
         * @param value the quantity
         * @return this builder
         */
        public Builder quantity(int value) {
            return put("quantity", Integer.valueOf(value));
        }

        /**
         * Builds the line. Constraints are checked when the order is sent.
         *
         * @return the immutable line
         */
        public EstampStampLine build() {
            return new EstampStampLine(new LinkedHashMap<>(fields));
        }
    }
}
