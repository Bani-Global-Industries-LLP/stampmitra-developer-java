package in.stampmitra.sdk.resources;

import in.stampmitra.sdk.internal.RequestExecutor;

/** Payments ({@code client.payments()}): currently the {@link #intents() intents} sub-resource. */
public final class PaymentsResource {

    private final PaymentIntentsResource intents;

    /**
     * Creates the resource. Use {@link in.stampmitra.sdk.StampMitra#payments()} instead.
     *
     * @param executor      the shared request executor
     * @param validateInput whether to validate inputs locally before sending
     */
    public PaymentsResource(RequestExecutor executor, boolean validateInput) {
        this.intents = new PaymentIntentsResource(executor, validateInput);
    }

    /**
     * Payment intents.
     *
     * @return the intents resource
     */
    public PaymentIntentsResource intents() {
        return intents;
    }
}
