package in.stampmitra.examples;

import in.stampmitra.sdk.RequestOptions;
import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.models.OrderDetail;
import in.stampmitra.sdk.models.OrderReference;
import in.stampmitra.sdk.models.OrderStatusResponse;
import in.stampmitra.sdk.requests.CreateOrderRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates an order and follows it up. Orders are identified by the {@code externalOrderId} that {@code create}
 * returns (there is no {@code id}).
 */
public final class CreateOrder {

    private CreateOrder() {
    }

    public static void main(String[] args) {
        StampMitra client = Examples.client();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rentAmount", 25000);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .externalOrderId("order-2026-0001")
                .externalCustomerId("customer-42")
                .serviceCode("estamp.instrument.rent_agreement")
                .details(details)
                .build();

        // The SDK sends an Idempotency-Key (auto-generated, reused across retries). Pass your own to make the
        // call safe to repeat from your side too, for example after a crash.
        OrderReference order = client.orders().create(request, RequestOptions.withIdempotencyKey("order-2026-0001"));
        String id = order.getExternalOrderId();
        System.out.println("created " + id + " at " + order.getCreatedAt());

        OrderStatusResponse status = client.orders().getStatus(id);
        System.out.println("status: " + status.getStatus());

        OrderDetail detail = client.orders().get(id);
        System.out.println("order: " + detail.getOrder());
        System.out.println("documents: " + client.orders().listDocuments(id).getDocuments());
    }
}
