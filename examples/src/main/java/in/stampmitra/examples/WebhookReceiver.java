package in.stampmitra.examples;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import in.stampmitra.sdk.Webhook;
import in.stampmitra.sdk.errors.SignatureVerificationException;
import in.stampmitra.sdk.models.WebhookEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * A webhook receiver using only the JDK's built-in HTTP server.
 *
 * <pre>
 * STAMPMITRA_WEBHOOK_SECRET=whsec_... java in.stampmitra.examples.WebhookReceiver
 * STAMPMITRA_WEBHOOK_SECRET=whsec_test_demo java in.stampmitra.examples.WebhookReceiver --demo   # self-test
 * </pre>
 *
 * <p>Key points: verify the signature over the <b>raw</b> body (read it as text before any JSON parsing), answer 2xx only
 * after verification, and answer quickly (do the real work asynchronously).
 */
public final class WebhookReceiver {

    private WebhookReceiver() {
    }

    static HttpServer start(int port, String secret) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/webhooks/stampmitra", exchange -> handle(exchange, secret));
        server.start();
        return server;
    }

    private static void handle(HttpExchange exchange, String secret) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed");
            return;
        }
        String rawBody;
        try (InputStream in = exchange.getRequestBody()) {
            rawBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            // Reads X-Webhook-Signature, X-Webhook-Timestamp and X-Webhook-Event-Id (case-insensitively).
            WebhookEvent event = Webhook.constructEventFromHeaders(rawBody, exchange.getRequestHeaders(), secret);
            System.out.println("verified " + event.getType() + " order=" + event.getCoreOrderId()
                    + " event-id=" + event.getEventId() + " at=" + event.getOccurredAt());
            respond(exchange, 200, "ok");
        } catch (SignatureVerificationException e) {
            System.out.println("rejected: " + e.getErrorCode());
            respond(exchange, 400, "invalid signature");
        }
    }

    private static void respond(HttpExchange exchange, int status, String text) throws IOException {
        byte[] out = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    public static void main(String[] args) throws Exception {
        String secret = System.getenv("STAMPMITRA_WEBHOOK_SECRET");
        if (secret == null || secret.isEmpty()) {
            System.err.println("Set STAMPMITRA_WEBHOOK_SECRET");
            System.exit(2);
        }
        boolean demo = args.length > 0 && args[0].equals("--demo");
        HttpServer server = start(demo ? 0 : 8080, secret);
        System.out.println("listening on http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks/stampmitra");
        if (demo) {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks/stampmitra";
            String body = "{\"coreOrderId\":\"core_order_9f3a1c2b8e4d4a5f\",\"coreEventType\":\"ORDER_CREATED\","
                    + "\"occurredAt\":\"2026-09-25T09:05:00.000Z\"}";
            String ts = String.valueOf(System.currentTimeMillis() / 1000);
            System.out.println("signed delivery -> " + post(url, body, ts, Webhook.sign(body, ts, secret)));
            System.out.println("tampered delivery -> " + post(url, body + " ", ts, Webhook.sign(body, ts, secret)));
            server.stop(0);
        }
    }

    private static int post(String url, String body, String timestamp, String signature) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty(Webhook.HEADER_TIMESTAMP, timestamp);
        c.setRequestProperty(Webhook.HEADER_SIGNATURE, signature);
        c.setRequestProperty(Webhook.HEADER_EVENT_ID, "evt_demo_1");
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return c.getResponseCode();
    }
}
