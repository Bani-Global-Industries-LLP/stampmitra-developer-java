package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.errors.ConnectionException;
import in.stampmitra.sdk.errors.RequestTimeoutException;
import in.stampmitra.sdk.http.JdkHttpTransport;
import in.stampmitra.sdk.http.TransportRequest;
import in.stampmitra.sdk.http.TransportResponse;
import in.stampmitra.sdk.support.ScriptedServer;
import in.stampmitra.sdk.support.ScriptedServer.Step;
import in.stampmitra.sdk.support.TestClients;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdkHttpTransportTest {

    private static TransportRequest request(String url, String method, String body, Duration timeout) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom", "yes");
        return new TransportRequest(method, URI.create(url), headers, body == null ? null : body.getBytes(StandardCharsets.UTF_8), timeout);
    }

    @Test
    void sendsRequestsAndReturnsEveryStatus() throws IOException {
        try (ScriptedServer s = new ScriptedServer(Step.json(404, "{\"error\":{\"code\":\"NOT_FOUND\"}}").header("X-Request-Id", "r-1"))) {
            TransportResponse r = new JdkHttpTransport().send(request(s.url() + "/v1/x?a=1&b=%20", "POST", "{\"k\":\"é₹\"}", Duration.ofSeconds(5)));
            assertThat(r.getStatus()).isEqualTo(404);
            assertThat(r.getHeaders().get("x-request-id")).containsExactly("r-1");
            assertThat(new String(r.getBody(), StandardCharsets.UTF_8)).contains("NOT_FOUND");
            ScriptedServer.Recorded rec = s.requests().get(0);
            assertThat(rec.method).isEqualTo("POST");
            assertThat(rec.path).isEqualTo("/v1/x");
            assertThat(rec.query).isEqualTo("a=1&b=%20");
            assertThat(rec.body).isEqualTo("{\"k\":\"é₹\"}");
            assertThat(rec.headers).containsEntry("x-custom", "yes").containsEntry("content-type", "application/json");
        }
    }

    @Test
    void emptyBodiesAndGetsWork() throws IOException {
        try (ScriptedServer s = new ScriptedServer(Step.status(204, null))) {
            TransportResponse r = new JdkHttpTransport().send(request(s.url() + "/", "GET", null, Duration.ofSeconds(5)));
            assertThat(r.getStatus()).isEqualTo(204);
            assertThat(r.getBody()).isEmpty();
            assertThat(s.requests().get(0).body).isEmpty();
        }
    }

    @Test
    void redirectsAreReturnedNotFollowed() throws IOException {
        try (ScriptedServer s = new ScriptedServer(Step.status(301, "").header("Location", "http://127.0.0.1:1/"))) {
            assertThat(new JdkHttpTransport().send(request(s.url() + "/", "GET", null, Duration.ofSeconds(5))).getStatus())
                    .isEqualTo(301);
        }
    }

    @Test
    void slowResponsesTimeOut() throws IOException {
        try (ScriptedServer s = new ScriptedServer(Step.slow(1500, 200, "{}"))) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> new JdkHttpTransport().send(request(s.url() + "/", "GET", null, Duration.ofMillis(300))))
                    .isInstanceOf(HttpTimeoutException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1400));
        }
    }

    @Test
    void connectionRefusedIsAnIoException() {
        assertThatThrownBy(() -> new JdkHttpTransport().send(request("http://127.0.0.1:1/", "GET", null, Duration.ofSeconds(3))))
                .isInstanceOf(IOException.class).isNotInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void droppedConnectionIsAnIoException() throws IOException {
        try (ScriptedServer s = new ScriptedServer(Step.drop())) {
            assertThatThrownBy(() -> new JdkHttpTransport().send(request(s.url() + "/", "POST", "{}", Duration.ofSeconds(3))))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void clientLevelErrorMappingForTimeoutsAndDrops() throws IOException {
        try (ScriptedServer slow = new ScriptedServer(Step.slow(1500, 200, "{}"));
             ScriptedServer drop = new ScriptedServer(Step.drop())) {
            StampMitra c = StampMitra.builder().apiKey(TestClients.KEY).baseUrl(slow.url()).maxRetries(0).build();
            assertThatThrownBy(() -> c.usage().summary(RequestOptions.builder().timeout(Duration.ofMillis(300)).build()))
                    .isExactlyInstanceOf(RequestTimeoutException.class);
            StampMitra d = StampMitra.builder().apiKey(TestClients.KEY).baseUrl(drop.url()).maxRetries(0).build();
            assertThatThrownBy(() -> d.usage().summary()).isExactlyInstanceOf(ConnectionException.class);
        }
    }

    @Test
    void transportModelsAreImmutableCopies() {
        byte[] body = {1, 2, 3};
        TransportRequest r = new TransportRequest("POST", URI.create("https://x.test/p?q=1"), Collections.singletonMap("A", "b"), body, Duration.ofSeconds(1));
        body[0] = 9;
        assertThat(r.getBody()[0]).isEqualTo((byte) 1);
        r.getBody()[1] = 9;
        assertThat(r.getBody()[1]).isEqualTo((byte) 2);
        assertThat(r.toString()).contains("POST https://x.test/p").doesNotContain("q=1");
        assertThat(new TransportResponse(200, Collections.singletonMap("X-A", Collections.singletonList("1")), null).getHeaders())
                .containsKey("x-a");
        assertThat(new TransportResponse(200, Collections.<String, java.util.List<String>>emptyMap(), null).toString()).contains("200");
    }
}
