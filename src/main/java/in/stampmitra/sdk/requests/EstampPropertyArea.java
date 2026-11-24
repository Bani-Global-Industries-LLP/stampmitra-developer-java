package in.stampmitra.sdk.requests;

import in.stampmitra.sdk.errors.ValidationException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Property area of a stamp line: a unit and a value. Needed when the state's {@code propertyAreaRequired} flag is
 * true. Immutable.
 */
public final class EstampPropertyArea {

    /** The four accepted units; {@link #wireName()} is the exact spelling the API requires. */
    public enum Unit {
        /** {@code Square Feet}. */
        SQUARE_FEET("Square Feet"),
        /** {@code Square Yard}. */
        SQUARE_YARD("Square Yard"),
        /** {@code Square Meters}. */
        SQUARE_METERS("Square Meters"),
        /** {@code Not Applicable}; no value is sent. */
        NOT_APPLICABLE("Not Applicable");

        private final String wire;

        Unit(String wire) {
            this.wire = wire;
        }

        /**
         * The spelling sent to the API.
         *
         * @return for example {@code Square Feet}
         */
        public String wireName() {
            return wire;
        }
    }

    private final Unit unit;
    private final BigDecimal value;

    private EstampPropertyArea(Unit unit, BigDecimal value) {
        this.unit = unit;
        this.value = value;
    }

    /**
     * An area in a real unit.
     *
     * @param unit  the unit; not {@link Unit#NOT_APPLICABLE}
     * @param value the area, greater than 0
     * @return the area
     * @throws ValidationException if the value is missing or not positive, or the unit is not applicable
     */
    public static EstampPropertyArea of(Unit unit, BigDecimal value) {
        Objects.requireNonNull(unit, "unit");
        if (unit == Unit.NOT_APPLICABLE) {
            return notApplicable();
        }
        if (value == null || value.signum() <= 0) {
            throw new ValidationException("propertyArea.value", "propertyArea.value must be greater than 0");
        }
        return new EstampPropertyArea(unit, value);
    }

    /**
     * An area in a real unit.
     *
     * @param unit  the unit
     * @param value the area, greater than 0
     * @return the area
     */
    public static EstampPropertyArea of(Unit unit, double value) {
        return of(unit, BigDecimal.valueOf(value));
    }

    /**
     * {@code Not Applicable}: the property has no area.
     *
     * @return the area
     */
    public static EstampPropertyArea notApplicable() {
        return new EstampPropertyArea(Unit.NOT_APPLICABLE, null);
    }

    /**
     * The unit.
     *
     * @return the unit
     */
    public Unit getUnit() {
        return unit;
    }

    /**
     * The value.
     *
     * @return the area, or {@code null} for {@link Unit#NOT_APPLICABLE}
     */
    public BigDecimal getValue() {
        return value;
    }

    /**
     * The JSON object sent to the API.
     *
     * @return an unmodifiable map with {@code unit} and, unless not applicable, {@code value}
     */
    public Map<String, Object> toWire() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unit", unit.wireName());
        if (value != null) {
            m.put("value", value);
        }
        return Collections.unmodifiableMap(m);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EstampPropertyArea && ((EstampPropertyArea) o).unit == unit
                && Objects.equals(((EstampPropertyArea) o).value, value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, value);
    }

    @Override
    public String toString() {
        return "EstampPropertyArea{" + unit.wireName() + (value == null ? "" : " " + value.toPlainString()) + "}";
    }
}
