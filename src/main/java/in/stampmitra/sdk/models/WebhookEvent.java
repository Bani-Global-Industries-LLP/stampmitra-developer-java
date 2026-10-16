package in.stampmitra.sdk.models;

import in.stampmitra.sdk.ResponseInfo;
import java.time.Instant;
import java.util.Map;

/**
 * A verified webhook delivery (see {@link in.stampmitra.sdk.Webhook#constructEvent(String, String, String, String)}).
 *
 * <p>The body holds {@code coreOrderId}, {@code coreEventType} and {@code occurredAt} followed by the event's own
 * fields; the extra fields vary by event type and are available through {@link #get(String)} and {@link #getRaw()}.
 */
public final class WebhookEvent extends Model {

    /**
     * Creates an event from a decoded payload.
     *
     * @param raw the decoded JSON object
     */
    public WebhookEvent(Map<String, Object> raw) {
        this(raw, null);
    }

    /**
     * Creates an event from a decoded payload.
     *
     * @param raw      the decoded JSON object
     * @param response unused for webhooks, may be {@code null}
     */
    public WebhookEvent(Map<String, Object> raw, ResponseInfo response) {
        super(raw, response);
    }

    /**
     * Internal order id the event is about.
     *
     * @return the id, or {@code null}
     */
    public String getCoreOrderId() {
        return string("coreOrderId");
    }

    /**
     * The upstream event type such as {@code ORDER_CREATED}.
     *
     * @return the type string, or {@code null}
     */
    public String getCoreEventType() {
        return string("coreEventType");
    }

    /**
     * The event as an enum (one of the eight {@code order.*} types), derived from {@code coreEventType}, or from a
     * {@code type} field such as {@code order.created} when the former is absent.
     *
     * @return the type, {@link WebhookEventType#UNKNOWN} for anything else
     */
    public WebhookEventType getType() {
        WebhookEventType fromCore = WebhookEventType.from(getCoreEventType());
        return fromCore != WebhookEventType.UNKNOWN ? fromCore : WebhookEventType.from(string("type"));
    }

    /**
     * When the event happened.
     *
     * @return the timestamp, or {@code null} if absent or not ISO-8601
     */
    public Instant getOccurredAt() {
        return instant("occurredAt");
    }

    /**
     * The delivery id from the {@code X-Webhook-Event-Id} header (added by the header based constructor), or an
     * {@code eventId} field of the payload.
     *
     * @return the id, or {@code null}
     */
    public String getEventId() {
        return string("eventId");
    }
}
