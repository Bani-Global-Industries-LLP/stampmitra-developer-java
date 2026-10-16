package in.stampmitra.sdk.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Masks credentials and personal data before anything reaches a logger or a {@code toString()}. Internal. */
public final class Redactor {

    private static final Set<String> SECRET_HEADERS = new HashSet<>(Arrays.asList(
            "authorization", "x-api-key", "cookie", "set-cookie", "proxy-authorization"));

    /** Identifier keys: only the last four characters stay visible. */
    private static final Set<String> IDENTIFIER_KEYS = new HashSet<>(Arrays.asList(
            "pan", "gstin", "accountnumber", "ifsc", "cin", "udyamnumber", "vehiclenumber", "regno", "dlnumber",
            "filenumber", "voterid", "phone", "din", "chassis", "engine", "micr", "utr", "email", "customermobile",
            "customeremail", "partypan", "partyaddress", "udyam"));

    /** Personal data keys: removed entirely. */
    private static final Set<String> PERSONAL_KEYS = new HashSet<>(Arrays.asList(
            "name", "businessname", "registeredname", "fathername", "ownername", "ownerfathername", "nameatbank",
            "fatherorhusbandname", "companyname", "tradename", "legalname", "dob", "address",
            "principalplaceofbusiness", "enterprisename", "customername", "partyname"));

    private static final Pattern KEY_LIKE = Pattern.compile("sk_(?:test|live)_[A-Za-z0-9_-]+");

    private Redactor() {
    }

    /**
     * Copy of the headers with credential values replaced.
     *
     * @param headers request or response headers
     * @return a map safe to log
     */
    public static Map<String, String> headers(Map<String, String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            out.put(e.getKey(), SECRET_HEADERS.contains(e.getKey().toLowerCase(Locale.ROOT)) ? "[REDACTED]" : e.getValue());
        }
        return out;
    }

    /**
     * Masks a parsed JSON structure.
     *
     * @param data a parsed JSON value
     * @return a masked copy
     */
    public static Object mask(Object data) {
        return mask(data, "");
    }

    private static Object mask(Object data, String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (data instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) data).entrySet()) {
                out.put(String.valueOf(e.getKey()), mask(e.getValue(), String.valueOf(e.getKey())));
            }
            return out;
        }
        if (data instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object o : (List<?>) data) {
                out.add(mask(o, key));
            }
            return out;
        }
        if (data == null) {
            return null;
        }
        if (PERSONAL_KEYS.contains(lower)) {
            return "[REDACTED]";
        }
        if (IDENTIFIER_KEYS.contains(lower)) {
            String s = String.valueOf(data);
            if (s.length() <= 4) {
                return "****";
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length() - 4; i++) {
                b.append('*');
            }
            return b.append(s.substring(s.length() - 4)).toString();
        }
        if (data instanceof String && KEY_LIKE.matcher((String) data).find()) {
            return KEY_LIKE.matcher((String) data).replaceAll("[REDACTED]");
        }
        return data;
    }

    /**
     * Masks a JSON body; non-JSON text is replaced with a length marker.
     *
     * @param body the raw body text, may be {@code null}
     * @return text safe to log
     */
    public static String body(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        try {
            Object parsed = Json.parse(body);
            if (!(parsed instanceof Map) && !(parsed instanceof List)) {
                return "[non-JSON body, " + body.length() + " chars]";
            }
            return Json.mapper().writeValueAsString(mask(parsed));
        } catch (JsonProcessingException e) {
            return "[non-JSON body, " + body.length() + " chars]";
        }
    }
}
