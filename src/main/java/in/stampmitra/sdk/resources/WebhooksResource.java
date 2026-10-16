package in.stampmitra.sdk.resources;

import in.stampmitra.sdk.internal.RequestExecutor;

/**
 * Webhooks ({@code client.webhooks()}): the {@link #events() events} sub-resource. To verify incoming deliveries use
 * {@link in.stampmitra.sdk.Webhook}.
 */
public final class WebhooksResource {

    private final WebhookEventsResource events;

    /**
     * Creates the resource. Use {@link in.stampmitra.sdk.StampMitra#webhooks()} instead.
     *
     * @param executor      the shared request executor
     * @param validateInput whether to validate inputs locally before sending
     */
    public WebhooksResource(RequestExecutor executor, boolean validateInput) {
        this.events = new WebhookEventsResource(executor, validateInput);
    }

    /**
     * Webhook delivery events.
     *
     * @return the events resource
     */
    public WebhookEventsResource events() {
        return events;
    }
}
