package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.errors.SignatureVerificationException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.internal.Json;
import in.stampmitra.sdk.models.WebhookEvent;
import in.stampmitra.sdk.models.WebhookEventType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class WebhookTest {

    private static final String SECRET = "whsec_test_0123456789abcdef";

    private static JsonNode file() throws IOException {
        try (InputStream in = WebhookTest.class.getResourceAsStream("/fixtures/webhook-signatures.json")) {
            return Json.mapper().readTree(in);
        }
    }

    @TestFactory
    List<DynamicTest> sharedVectors() throws IOException {
        JsonNode f = file();
        long now = f.get("now").asLong();
        long tolerance = f.get("toleranceSeconds").asLong();
        assertThat(tolerance).isEqualTo(Webhook.DEFAULT_TOLERANCE_SECONDS);
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode v : f.get("vectors")) {
            tests.add(DynamicTest.dynamicTest(v.get("name").asText(), () -> {
                byte[] payload = v.get("payload").asText().getBytes(StandardCharsets.UTF_8);
                Optional<String> reason = Webhook.failureReason(payload, v.get("timestamp").asText(),
                        v.get("signature").asText(), v.get("secret").asText(), tolerance, now);
                if (v.get("expected").get("valid").asBoolean()) {
                    assertThat(reason).isEmpty();
                } else {
                    assertThat(reason).contains(v.get("expected").get("reason").asText());
                }
            }));
        }
        assertThat(tests.size()).isGreaterThanOrEqualTo(24);
        return tests;
    }

    @Test
    void signMatchesTheValidVector() throws IOException {
        JsonNode v = file().get("vectors").get(0);
        assertThat(Webhook.sign(v.get("payload").asText(), v.get("timestamp").asText(), v.get("secret").asText()))
                .isEqualTo(v.get("signature").asText());
    }

    @Test
    void verifySignatureUsesTheCurrentClock() {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String body = "{\"coreOrderId\":\"o\"}";
        String sig = Webhook.sign(body, ts, SECRET);
        assertThat(Webhook.verifySignature(body, ts, sig, SECRET)).isTrue();
        assertThat(Webhook.verifySignature(body.getBytes(StandardCharsets.UTF_8), ts, sig, SECRET)).isTrue();
        assertThat(Webhook.verifySignature(body + " ", ts, sig, SECRET)).isFalse();
        String old = String.valueOf(System.currentTimeMillis() / 1000 - 600);
        assertThat(Webhook.verifySignature(body, old, Webhook.sign(body, old, SECRET), SECRET)).isFalse();
        assertThat(Webhook.verifySignature(body, old, Webhook.sign(body, old, SECRET), SECRET, 900)).isTrue();
        assertThat(Webhook.verifySignature(body, ts, null, SECRET)).isFalse();
        assertThat(Webhook.verifySignature(body, null, sig, SECRET)).isFalse();
    }

    @Test
    void emptySecretIsRejected() {
        for (String secret : new String[] {"", null}) {
            assertThatThrownBy(() -> Webhook.verifySignature("{}", "1", "x", secret))
                    .isInstanceOf(SignatureVerificationException.class)
                    .satisfies(e -> assertThat(((SignatureVerificationException) e).getErrorCode())
                            .isEqualTo("MISSING_SECRET"));
            assertThatThrownBy(() -> Webhook.sign("{}", "1", secret)).isInstanceOf(SignatureVerificationException.class);
        }
    }

    @Test
    void negativeToleranceIsRejected() {
        assertThatThrownBy(() -> Webhook.failureReason("{}".getBytes(StandardCharsets.UTF_8), "1", "x", SECRET, -1, 1))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void errorsNeverContainTheSecret() {
        assertThatThrownBy(() -> Webhook.constructEvent("{}", "1", "bad", SECRET))
                .isInstanceOf(SignatureVerificationException.class).satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(SECRET);
                    assertThat(((SignatureVerificationException) e).getErrorCode()).isEqualTo("TIMESTAMP_OUTSIDE_TOLERANCE");
                });
    }

    private static String ts() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    @Test
    void constructEventReturnsATypedEvent() {
        String body = "{\"coreOrderId\":\"core_1\",\"coreEventType\":\"PAYMENT_CONFIRMED\","
                + "\"occurredAt\":\"2026-09-25T09:05:00.000Z\",\"amountMinor\":50000,\"extra\":{\"a\":1}}";
        String ts = ts();
        WebhookEvent e = Webhook.constructEvent(body, ts, Webhook.sign(body, ts, SECRET), SECRET);
        assertThat(e.getCoreOrderId()).isEqualTo("core_1");
        assertThat(e.getCoreEventType()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(e.getType()).isEqualTo(WebhookEventType.ORDER_PAYMENT_CONFIRMED);
        assertThat(e.getOccurredAt()).isEqualTo(Instant.parse("2026-09-25T09:05:00Z"));
        assertThat(e.get("amountMinor")).isEqualTo(50000);
        assertThat(e.get("extra")).isEqualTo(Collections.singletonMap("a", 1));
        assertThat(e.getRaw()).containsKeys("coreOrderId", "amountMinor", "extra");
    }

    @Test
    void allEightEventTypesMapBothSpellings() {
        String[][] pairs = {{"ORDER_CREATED", "order.created"}, {"PAYMENT_CONFIRMED", "order.payment_confirmed"},
                {"FULFILMENT_STARTED", "order.fulfilment_started"}, {"FULFILMENT_COMPLETED", "order.fulfilment_completed"},
                {"ORDER_FAILED", "order.failed"}, {"ORDER_CANCELLED", "order.cancelled"},
                {"REFUND_COMPLETED", "order.refund_completed"}, {"DOCUMENT_READY", "order.document_ready"}};
        for (String[] p : pairs) {
            assertThat(WebhookEventType.from(p[0])).isNotEqualTo(WebhookEventType.UNKNOWN)
                    .isEqualTo(WebhookEventType.from(p[1]));
            assertThat(WebhookEventType.from(p[0]).wireName()).isEqualTo(p[1]);
        }
        assertThat(WebhookEventType.from("order.exploded")).isEqualTo(WebhookEventType.UNKNOWN);
        assertThat(WebhookEventType.from(null)).isEqualTo(WebhookEventType.UNKNOWN);
        assertThat(WebhookEventType.values()).hasSize(9);
    }

    @Test
    void typeFallsBackToATypeField() {
        WebhookEvent e = new WebhookEvent(Collections.<String, Object>singletonMap("type", "order.failed"));
        assertThat(e.getType()).isEqualTo(WebhookEventType.ORDER_FAILED);
        assertThat(e.getOccurredAt()).isNull();
    }

    @Test
    void constructEventFromHeadersIsCaseInsensitiveAndAddsTheEventId() {
        String body = "{\"coreOrderId\":\"core_1\",\"coreEventType\":\"ORDER_CREATED\",\"occurredAt\":\"2026-09-25T09:05:00.000Z\"}";
        String ts = ts();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("x-webhook-signature", Collections.singletonList(Webhook.sign(body, ts, SECRET)));
        headers.put("X-WEBHOOK-TIMESTAMP", Collections.singletonList(ts));
        headers.put("X-Webhook-Event-Id", Arrays.asList("evt_1", "evt_2"));
        WebhookEvent e = Webhook.constructEventFromHeaders(body, headers, SECRET);
        assertThat(e.getEventId()).isEqualTo("evt_1");
        headers.remove("x-webhook-signature");
        assertThatThrownBy(() -> Webhook.constructEventFromHeaders(body, headers, SECRET))
                .isInstanceOf(SignatureVerificationException.class).satisfies(x ->
                        assertThat(((SignatureVerificationException) x).getErrorCode()).isEqualTo("MISSING_HEADERS"));
    }

    @Test
    void nonObjectPayloadsAreRejectedAfterVerification() {
        String ts = ts();
        for (String body : new String[] {"[1,2]", "\"str\"", "not json", ""}) {
            assertThatThrownBy(() -> Webhook.constructEvent(body, ts, Webhook.sign(body, ts, SECRET), SECRET))
                    .isInstanceOf(SignatureVerificationException.class).satisfies(e ->
                            assertThat(((SignatureVerificationException) e).getErrorCode()).isEqualTo("INVALID_PAYLOAD"));
        }
    }

    @Test
    void bytePayloadsAreSignedOverTheExactBytes() {
        byte[] body = new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xe0, (byte) 0xa4, (byte) 0x95, '"', '}'};
        String ts = ts();
        String sig = Webhook.sign(body, ts, SECRET);
        assertThat(Webhook.verifySignature(body, ts, sig, SECRET)).isTrue();
        WebhookEvent e = Webhook.constructEvent(body, ts, sig, SECRET, 300, null, System.currentTimeMillis() / 1000);
        assertThat(e.get("a")).isEqualTo("क");
    }
}
