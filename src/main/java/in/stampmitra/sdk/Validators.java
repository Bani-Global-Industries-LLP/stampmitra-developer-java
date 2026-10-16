package in.stampmitra.sdk;

import in.stampmitra.sdk.errors.ValidationException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Client-side input validators. The patterns mirror the API's DTO validators, so malformed input fails locally with a
 * {@link ValidationException} (no network call, nothing billed).
 *
 * <p>Every method trims its input and, except for {@link #bankAccount(String)}, upper-cases it; it returns the
 * normalised value that the SDK then sends. Separators are <em>not</em> stripped: {@code "MH-12 AB 1234"} is rejected,
 * exactly as the API rejects it.
 */
public final class Validators {

    /** PAN, for example {@code ABCDE1234F}. */
    public static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    /** GSTIN, for example {@code 27ABCDE1234F1Z5}. */
    public static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    /** IFSC, for example {@code ICIC0001234}. */
    public static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    /** CIN, for example {@code U74999DL2015PTC284009}. */
    public static final Pattern CIN = Pattern.compile("^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$");
    /** Udyam registration number, for example {@code UDYAM-MH-12-1234567}. */
    public static final Pattern UDYAM = Pattern.compile("^UDYAM-[A-Z]{2}-[0-9]{2}-[0-9]{7}$");
    /** Vehicle registration number, for example {@code MH12AB1234}. */
    public static final Pattern VEHICLE_NUMBER = Pattern.compile("^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}$");
    /** Bank account number: 9 to 18 digits. */
    public static final Pattern BANK_ACCOUNT = Pattern.compile("^[0-9]{9,18}$");
    /** Passport file number, for example {@code PA1079341954215}. */
    public static final Pattern PASSPORT_FILE_NUMBER = Pattern.compile("^[A-Z]{2}[0-9]{13}$");
    /** Driving licence number, for example {@code MH1420110012345}. */
    public static final Pattern DRIVING_LICENSE = Pattern.compile("^[A-Z]{2}[0-9]{2}[0-9]{11}$");
    /** Voter id (EPIC), for example {@code ABC1234567}. */
    public static final Pattern VOTER_ID = Pattern.compile("^[A-Z]{3}[0-9]{7}$");
    /** Indian mobile number with optional +91 / 91 / 0 prefix. */
    public static final Pattern PHONE = Pattern.compile("^(?:\\+?91|0)?[6-9][0-9]{9}$");

    private static final Pattern ISO_DATE = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[T ][0-9:.]+(?:Z|[+-][0-9:]+)?)?$");

    private Validators() {
    }

    /**
     * Validates a PAN.
     *
     * @param value the PAN
     * @return the normalised PAN
     * @throws ValidationException if it does not match {@code ABCDE1234F}
     */
    public static String pan(String value) {
        return pan(value, "pan");
    }

    /**
     * Validates a PAN and reports errors against a custom field name.
     *
     * @param value the PAN
     * @param field the field name used in the error
     * @return the normalised PAN
     * @throws ValidationException if it does not match {@code ABCDE1234F}
     */
    public static String pan(String value, String field) {
        return match(value, PAN, field, "must match the format ABCDE1234F");
    }

    /**
     * Validates a GSTIN.
     *
     * @param value the GSTIN
     * @return the normalised GSTIN
     * @throws ValidationException if it is not a valid 15 character GSTIN
     */
    public static String gstin(String value) {
        return match(value, GSTIN, "gstin", "must be a valid 15 character GSTIN, e.g. 27ABCDE1234F1Z5");
    }

    /**
     * Validates an IFSC code.
     *
     * @param value the IFSC
     * @return the normalised IFSC
     * @throws ValidationException if it does not match {@code ICIC0001234}
     */
    public static String ifsc(String value) {
        return match(value, IFSC, "ifsc", "must match the format ICIC0001234");
    }

    /**
     * Validates a CIN.
     *
     * @param value the CIN
     * @return the normalised CIN
     * @throws ValidationException if it does not match {@code U74999DL2015PTC284009}
     */
    public static String cin(String value) {
        return match(value, CIN, "cin", "must match the format U74999DL2015PTC284009");
    }

    /**
     * Validates a Udyam registration number.
     *
     * @param value the Udyam number
     * @return the normalised number
     * @throws ValidationException if it does not match {@code UDYAM-XX-00-0000000}
     */
    public static String udyam(String value) {
        return match(value, UDYAM, "udyamNumber", "must match the format UDYAM-XX-00-0000000");
    }

    /**
     * Validates a vehicle registration number (trimmed and upper-cased only).
     *
     * @param value the registration number
     * @return the normalised number
     * @throws ValidationException if it is not like {@code MH12AB1234}
     */
    public static String vehicleNumber(String value) {
        return match(value, VEHICLE_NUMBER, "vehicleNumber", "must be an Indian vehicle registration number, e.g. MH12AB1234");
    }

    /**
     * Validates a bank account number (trimmed only; digits are required).
     *
     * @param value the account number
     * @return the trimmed account number
     * @throws ValidationException if it is not 9 to 18 digits
     */
    public static String bankAccount(String value) {
        String clean = value == null ? "" : value.trim();
        if (!BANK_ACCOUNT.matcher(clean).matches()) {
            throw new ValidationException("accountNumber", "accountNumber must be a 9-18 digit numeric bank account number");
        }
        return clean;
    }

    /**
     * Validates a passport file number.
     *
     * @param value the file number
     * @return the normalised number
     * @throws ValidationException if it does not match {@code PA1079341954215}
     */
    public static String passportFileNumber(String value) {
        return match(value, PASSPORT_FILE_NUMBER, "fileNumber", "must match the format PA1079341954215");
    }

    /**
     * Validates a driving licence number.
     *
     * @param value the licence number
     * @return the normalised number
     * @throws ValidationException if it does not match {@code MH1420110012345}
     */
    public static String drivingLicense(String value) {
        return match(value, DRIVING_LICENSE, "dlNumber", "must match the format MH1420110012345");
    }

    /**
     * Validates a voter id.
     *
     * @param value the voter id
     * @return the normalised voter id
     * @throws ValidationException if it does not match {@code ABC1234567}
     */
    public static String voterId(String value) {
        return match(value, VOTER_ID, "voterId", "must match the format ABC1234567");
    }

    /**
     * Validates an Indian mobile number.
     *
     * @param value the number
     * @return the trimmed number
     * @throws ValidationException if it is not a valid 10-digit Indian mobile number
     */
    public static String phone(String value) {
        String clean = value == null ? "" : value.trim();
        if (!PHONE.matcher(clean).matches()) {
            throw new ValidationException("phone", "phone must be a valid 10-digit Indian mobile number");
        }
        return clean;
    }

    /**
     * Validates an ISO date such as {@code 1990-01-31} (a trailing time part is accepted).
     *
     * @param value the date
     * @param field the field name used in the error
     * @return the trimmed date
     * @throws ValidationException if it is not a real calendar date in ISO format
     */
    public static String date(String value, String field) {
        String clean = value == null ? "" : value.trim();
        boolean ok = ISO_DATE.matcher(clean).matches();
        if (ok) {
            try {
                LocalDate.parse(clean.substring(0, 10));
            } catch (DateTimeException e) {
                ok = false;
            }
        }
        if (!ok) {
            throw new ValidationException(field, field + " must be an ISO date, e.g. 1990-01-31");
        }
        return clean;
    }

    /**
     * Validates a free-text name (1 to 256 characters after trimming).
     *
     * @param value the name
     * @param field the field name used in the error
     * @return the trimmed name
     * @throws ValidationException if it is empty or longer than 256 characters
     */
    public static String name(String value, String field) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > 256) {
            throw new ValidationException(field, field + " must be 1-256 characters");
        }
        return clean;
    }

    private static String match(String value, Pattern pattern, String field, String hint) {
        String clean = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!pattern.matcher(clean).matches()) {
            throw new ValidationException(field, field + " " + hint);
        }
        return clean;
    }
}
