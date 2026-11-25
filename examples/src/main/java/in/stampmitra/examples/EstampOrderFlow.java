package in.stampmitra.examples;

import in.stampmitra.sdk.RequestOptions;
import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.errors.PaymentRequiredException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.models.EstampCertificate;
import in.stampmitra.sdk.models.EstampOrder;
import in.stampmitra.sdk.models.EstampStamp;
import in.stampmitra.sdk.models.EstampValidateResult;
import in.stampmitra.sdk.requests.DocumentUpload;
import in.stampmitra.sdk.requests.EstampOrderRequest;
import in.stampmitra.sdk.requests.EstampParty;
import in.stampmitra.sdk.requests.EstampStampLine;
import java.nio.charset.StandardCharsets;

/**
 * The e-Stamp flow end to end: validate (dry run) -> create with an idempotency key -> upload the payer's PAN card ->
 * list the stamps -> fetch a certificate. An order can be placed without a PAN card, but a PAN card is mandatory to complete the stamp.
 */
public final class EstampOrderFlow {

    private EstampOrderFlow() {
    }

    public static void main(String[] args) {
        StampMitra client = Examples.client();

        EstampOrderRequest request = EstampOrderRequest.builder()
                .reference("order-2026-0917")
                .addStamp(EstampStampLine.builder()
                        .clientReference("line-001")
                        .state("MH")
                        .articleCode("ART1")
                        .denomination(1000)
                        .firstParty(EstampParty.builder("Jane Doe").address("12 MG Road, Pune").build())
                        .secondParty("John Smith")
                        .purpose("Rental Agreement")
                        .build())
                .build();

        // 1. Dry run: nothing is created and nothing is billed.
        EstampValidateResult check = client.estamp().orders().validate(request);
        System.out.println("valid=" + check.isValid() + " status=" + check.getStatus());

        // 2. Create. Derive the key from something stable you own and persist it before sending, so that a retry
        //    after a crash replays the original order instead of creating a second one.
        String key = "lease-2026-0412-v1";
        EstampOrder order;
        try {
            order = client.estamp().orders().create(request, RequestOptions.withIdempotencyKey(key));
        } catch (ValidationException e) {
            e.getFieldErrors().forEach(i -> System.err.println(i.getField() + " " + i.getCode() + ": " + i.getMessage()));
            return;
        } catch (PaymentRequiredException e) {
            System.err.println("top up the " + e.getWallet() + " wallet, then retry with the same key");
            return;
        }
        System.out.println("order " + order.getId() + " " + order.getStatus());

        // 3. Upload the payer's PAN card (PDF, JPEG or PNG up to 10 MB). With a real file: DocumentUpload.of(Paths.get("pan.pdf")).
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        System.out.println("uploaded " + client.estamp().orders().uploadDocument(order.getId(), DocumentUpload.of("pan.pdf", pdf)).getDocumentId());

        // 4. List the stamps and 5. fetch a certificate once it is available.
        for (EstampStamp stamp : client.estamp().orders().listStamps(order.getId()).getItems()) {
            System.out.println("stamp " + stamp.getId() + " " + stamp.getStatus() + " pan=" + stamp.getPanCard().getStatus());
            EstampCertificate certificate = client.estamp().stamps().getCertificate(stamp.getId());
            System.out.println(certificate.isAvailable() ? "certificate: " + certificate.getDownloadUrl() : "certificate: " + certificate.getStatus());
        }
    }
}
