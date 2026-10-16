package in.stampmitra.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import in.stampmitra.sdk.errors.SignatureVerificationException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.internal.Json;
import in.stampmitra.sdk.models.WebhookEvent;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies webhook deliveries.
 *
 * <p>Scheme: {@code signature = hex(HMAC-SHA256(secret, "{timestamp}.{rawBody}"))}, where {@code rawBody} is the
 * request body exactly as received (do <b>not</b> parse and re-serialise it) and {@code timestamp} is unix seconds.
 * Deliveries carry the headers {@value #HEADER_SIGNATURE}, {@value #HEADER_TIMESTAMP} and {@value #HEADER_EVENT_ID}.
 * Signatures are compared in constant time and timestamps further than the tolerance (default 300 seconds) from now
 * are rejected.
 *
 * <pre>{@code
 * String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
 * WebhookEvent event = Webhook.constructEvent(body, timestampHeader, signatureHeader, secret);
 * }</pre>
 */
public final class Webhook {

    /** Header carrying the hex signature. */
    public static final String HEADER_SIGNATURE = "X-Webhook-Signature";
    /** Header carrying the unix timestamp (seconds) that was signed. */
    public static final String HEADER_TIMESTAMP = "X-Webhook-Timestamp";
    /** Header carrying the delivery id. */
    public static final String HEADER_EVENT_ID = "X-Webhook-Event-Id";
    /** Default timestamp tolerance in seconds. */
    public static final long DEFAULT_TOLERANCE_SECONDS = 300L;

    /** Failure reason: the timestamp is not a unix-seconds integer. */
    public static final String REASON_INVALID_TIMESTAMP = "invalid_timestamp";
    /** Failure reason: the timestamp is further from now than the tolerance. */
    public static final String REASON_OUTSIDE_TOLERANCE = "timestamp_outside_tolerance";
    /** Failure reason: the signature does not match. */
    public static final String REASON_MISMATCH = "signature_mismatch";

    private static final Pattern TIMESTAMP = Pattern.compile("^[0-9]{1,15}$");

    private Webhook() {
    }

    /**
     * Computes the expected signature. Useful for tests and local replays.
     *
     * @param payload   the raw request body
     * @param timestamp the unix timestamp in seconds, as sent in the header
     * @param secret    the webhook signing secret
     * @return the lower-case hex HMAC-SHA256
     * @throws SignatureVerificationException if the secret is empty
     */
    public static String sign(String payload, String timestamp, String secret) {
        return sign(payload.getBytes(StandardCharsets.UTF_8), timestamp, secret);
    }

    /**
     * Computes the expected signature over raw body bytes.
     *
     * @param payload   the raw request body
     * @param timestamp the unix timestamp in seconds, as sent in the header
     * @param secret    the webhook signing secret
     * @return the lower-case hex HMAC-SHA256
     * @throws SignatureVerificationException if the secret is empty
     */
    public static String sign(byte[] payload, String timestamp, String secret) {
        requireSecret(secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(payload);
            byte[] digest = mac.doFinal();
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /**
     * Why verification fails, or empty when the delivery is authentic and fresh.
     *
     * @param payload          the raw request body
     * @param timestamp        value of {@value #HEADER_TIMESTAMP}
     * @param signature        value of {@value #HEADER_SIGNATURE}
     * @param secret           the webhook signing secret
     * @param toleranceSeconds maximum allowed distance between the timestamp and {@code nowEpochSeconds}
     * @param nowEpochSeconds  the current unix time in seconds (injectable for tests)
     * @return one of the {@code REASON_*} constants, or empty when valid
     * @throws SignatureVerificationException if the secret is empty
     * @throws ValidationException            if the tolerance is negative
     */
    public static Optional<String> failureReason(byte[] payload, String timestamp, String signature, String secret,
                                                 long toleranceSeconds, long nowEpochSeconds) {
        requireSecret(secret);
        if (toleranceSeconds < 0) {
            throw new ValidationException("toleranceSeconds", "toleranceSeconds must be >= 0");
        }
        if (timestamp == null || !TIMESTAMP.matcher(timestamp).matches()) {
            return Optional.of(REASON_INVALID_TIMESTAMP);
        }
        if (Math.abs(nowEpochSeconds - Long.parseLong(timestamp)) > toleranceSeconds) {
            return Optional.of(REASON_OUTSIDE_TOLERANCE);
        }
        byte[] expected = sign(payload, timestamp, secret).getBytes(StandardCharsets.UTF_8);
        byte[] provided = signature == null ? new byte[0] : signature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            return Optional.of(REASON_MISMATCH);
        }
        return Optional.empty();
    }

    /**
     * Why verification fails, or empty when valid, using the current time.
     *
     * @param payload          the raw request body
     * @param timestamp        value of {@value #HEADER_TIMESTAMP}
     * @param signature        value of {@value #HEADER_SIGNATURE}
     * @param secret           the webhook signing secret
     * @param toleranceSeconds maximum allowed clock distance in seconds
     * @return one of the {@code REASON_*} constants, or empty when valid
     */
    public static Optional<String> failureReason(String payload, String timestamp, String signature, String secret,
                                                 long toleranceSeconds) {
        return failureReason(payload.getBytes(StandardCharsets.UTF_8), timestamp, signature, secret, toleranceSeconds,
                System.currentTimeMillis() / 1000L);
    }

    /**
     * Checks the signature and freshness of a delivery (tolerance 300 seconds).
     *
     * @param payload   the raw request body, exactly as received
     * @param timestamp value of {@value #HEADER_TIMESTAMP}
     * @param signature value of {@value #HEADER_SIGNATURE}
     * @param secret    the webhook signing secret
     * @return {@code true} if authentic and fresh
     * @throws SignatureVerificationException if the secret is empty
     */
    public static boolean verifySignature(String payload, String timestamp, String signature, String secret) {
        return verifySignature(payload, timestamp, signature, secret, DEFAULT_TOLERANCE_SECONDS);
    }

    /**
     * Checks the signature and freshness of a delivery.
     *
     * @param payload          the raw request body, exactly as received
     * @param timestamp        value of {@value #HEADER_TIMESTAMP}
     * @param signature        value of {@value #HEADER_SIGNATURE}
     * @param secret           the webhook signing secret
     * @param toleranceSeconds maximum allowed clock distance in seconds
     * @return {@code true} if authentic and fresh
     * @throws SignatureVerificationException if the secret is empty
     */
    public static boolean verifySignature(String payload, String timestamp, String signature, String secret,
                                          long toleranceSeconds) {
        return !failureReason(payload, timestamp, signature, secret, toleranceSeconds).isPresent();
    }

    /**
     * Checks the signature and freshness of a delivery whose body is available as bytes (tolerance 300 seconds).
     *
     * @param payload   the raw request body bytes, exactly as received
     * @param timestamp value of {@value #HEADER_TIMESTAMP}
     * @param signature value of {@value #HEADER_SIGNATURE}
     * @param secret    the webhook signing secret
     * @return {@code true} if authentic and fresh
     * @throws SignatureVerificationException if the secret is empty
     */
    public static boolean verifySignature(byte[] payload, String timestamp, String signature, String secret) {
        return !failureReason(payload, timestamp, signature, secret, DEFAULT_TOLERANCE_SECONDS,
                System.currentTimeMillis() / 1000L).isPresent();
    }

    /**
     * Verifies a delivery and parses it into a typed event (tolerance 300 seconds).
     *
     * @param payload   the raw request body, exactly as received
     * @param timestamp value of {@value #HEADER_TIMESTAMP}
     * @param signature value of {@value #HEADER_SIGNATURE}
     * @param secret    the webhook signing secret
     * @return the event
     * @throws SignatureVerificationException if verification fails or the payload is not a JSON object
     */
    public static WebhookEvent constructEvent(String payload, String timestamp, String signature, String secret) {
        return constructEvent(payload.getBytes(StandardCharsets.UTF_8), timestamp, signature, secret,
                DEFAULT_TOLERANCE_SECONDS, null, System.currentTimeMillis() / 1000L);
    }

    /**
     * Verifies a delivery and parses it into a typed event.
     *
     * @param payload          the raw request body bytes, exactly as received
     * @param timestamp        value of {@value #HEADER_TIMESTAMP}
     * @param signature        value of {@value #HEADER_SIGNATURE}
     * @param secret           the webhook signing secret
     * @param toleranceSeconds maximum allowed clock distance in seconds
     * @param eventId          value of {@value #HEADER_EVENT_ID}, stored as {@code eventId} when the payload has none;
     *                         may be {@code null}
     * @param nowEpochSeconds  the current unix time in seconds (injectable for tests)
     * @return the event
     * @throws SignatureVerificationException if verification fails or the payload is not a JSON object
     */
    public static WebhookEvent constructEvent(byte[] payload, String timestamp, String signature, String secret,
                                              long toleranceSeconds, String eventId, long nowEpochSeconds) {
        Optional<String> reason = failureReason(payload, timestamp, signature, secret, toleranceSeconds,
                nowEpochSeconds);
        if (reason.isPresent()) {
            throw new SignatureVerificationException("Webhook verification failed: " + reason.get(),
                    reason.get().toUpperCase(Locale.ROOT));
        }
        Object parsed;
        try {
            parsed = Json.parse(new String(payload, StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new SignatureVerificationException("Webhook payload is not valid JSON", "INVALID_PAYLOAD");
        }
        if (!(parsed instanceof Map)) {
            throw new SignatureVerificationException("Webhook payload is not a JSON object", "INVALID_PAYLOAD");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) parsed).entrySet()) {
            data.put(String.valueOf(e.getKey()), e.getValue());
        }
        if (eventId != null && !data.containsKey("eventId")) {
            data.put("eventId", eventId);
        }
        return new WebhookEvent(data);
    }

    /**
     * Convenience wrapper that reads the three delivery headers (names are case-insensitive) and verifies with the
     * default tolerance. Works directly with {@code com.sun.net.httpserver.Headers}, Servlet header maps and the
     * like.
     *
     * @param payload the raw request body, exactly as received
     * @param headers request headers, name to values
     * @param secret  the webhook signing secret
     * @return the event
     * @throws SignatureVerificationException if a header is missing, verification fails or the payload is invalid
     */
    public static WebhookEvent constructEventFromHeaders(String payload,
                                                         Map<String, ? extends Collection<String>> headers,
                                                         String secret) {
        String signature = header(headers, HEADER_SIGNATURE);
        String timestamp = header(headers, HEADER_TIMESTAMP);
        if (signature == null || timestamp == null) {
            throw new SignatureVerificationException("Missing " + HEADER_SIGNATURE + " or " + HEADER_TIMESTAMP
                    + " header", "MISSING_HEADERS");
        }
        return constructEvent(payload.getBytes(StandardCharsets.UTF_8), timestamp, signature, secret,
                DEFAULT_TOLERANCE_SECONDS, header(headers, HEADER_EVENT_ID), System.currentTimeMillis() / 1000L);
    }

    private static String header(Map<String, ? extends Collection<String>> headers, String name) {
        for (Map.Entry<String, ? extends Collection<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null
                    && !e.getValue().isEmpty()) {
                return e.getValue().iterator().next();
            }
        }
        return null;
    }

    private static void requireSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new SignatureVerificationException("Webhook secret must not be empty", "MISSING_SECRET");
        }
    }
}
