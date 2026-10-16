package in.stampmitra.examples.spring;

import in.stampmitra.sdk.Webhook;
import in.stampmitra.sdk.errors.SignatureVerificationException;
import in.stampmitra.sdk.models.WebhookEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot webhook endpoint. Take the body as {@code byte[]}: the signature covers the raw bytes, so never let Spring
 * parse the JSON first.
 */
@RestController
public class WebhookController {

    private final String secret;

    public WebhookController(@Value("${stampmitra.webhook-secret}") String secret) {
        this.secret = secret;
    }

    @PostMapping("/webhooks/stampmitra")
    public ResponseEntity<String> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(Webhook.HEADER_TIMESTAMP) String timestamp,
            @RequestHeader(Webhook.HEADER_SIGNATURE) String signature,
            @RequestHeader(value = Webhook.HEADER_EVENT_ID, required = false) String eventId) {
        try {
            WebhookEvent event = Webhook.constructEvent(rawBody, timestamp, signature, secret,
                    Webhook.DEFAULT_TOLERANCE_SECONDS, eventId, System.currentTimeMillis() / 1000L);
            System.out.println("verified " + event.getType() + " order=" + event.getCoreOrderId());
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature: " + e.getErrorCode());
        }
    }
}
