package in.stampmitra.sdk.models;

import java.util.Locale;

/** The order lifecycle events the API delivers by webhook. */
public enum WebhookEventType {
    /** An order was created ({@code order.created}). */
    ORDER_CREATED("order.created"),
    /** Payment for an order was confirmed ({@code order.payment_confirmed}). */
    ORDER_PAYMENT_CONFIRMED("order.payment_confirmed"),
    /** Fulfilment started ({@code order.fulfilment_started}). */
    ORDER_FULFILMENT_STARTED("order.fulfilment_started"),
    /** Fulfilment completed ({@code order.fulfilment_completed}). */
    ORDER_FULFILMENT_COMPLETED("order.fulfilment_completed"),
    /** The order failed ({@code order.failed}). */
    ORDER_FAILED("order.failed"),
    /** The order was cancelled ({@code order.cancelled}). */
    ORDER_CANCELLED("order.cancelled"),
    /** A refund completed ({@code order.refund_completed}). */
    ORDER_REFUND_COMPLETED("order.refund_completed"),
    /** A document became available ({@code order.document_ready}). */
    ORDER_DOCUMENT_READY("order.document_ready"),
    /** An event type this SDK version does not know. The raw payload is still available. */
    UNKNOWN("unknown");

    private final String wireName;

    WebhookEventType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The webhook name used in the API documentation, for example {@code order.created}.
     *
     * @return the name
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Maps a webhook name ({@code order.created}) or an upstream event type ({@code ORDER_CREATED},
     * {@code PAYMENT_CONFIRMED}) to the enum.
     *
     * @param name either spelling, may be {@code null}
     * @return the matching constant, or {@link #UNKNOWN}
     */
    public static WebhookEventType from(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        for (WebhookEventType t : values()) {
            if (t != UNKNOWN && t.wireName.equals(lower)) {
                return t;
            }
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        switch (upper) {
            case "ORDER_CREATED":
                return ORDER_CREATED;
            case "PAYMENT_CONFIRMED":
                return ORDER_PAYMENT_CONFIRMED;
            case "FULFILMENT_STARTED":
                return ORDER_FULFILMENT_STARTED;
            case "FULFILMENT_COMPLETED":
                return ORDER_FULFILMENT_COMPLETED;
            case "ORDER_FAILED":
                return ORDER_FAILED;
            case "ORDER_CANCELLED":
                return ORDER_CANCELLED;
            case "REFUND_COMPLETED":
                return ORDER_REFUND_COMPLETED;
            case "DOCUMENT_READY":
                return ORDER_DOCUMENT_READY;
            default:
                return UNKNOWN;
        }
    }
}
