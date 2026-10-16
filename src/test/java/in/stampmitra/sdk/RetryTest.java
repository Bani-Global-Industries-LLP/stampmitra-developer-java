package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.errors.ConflictException;
import in.stampmitra.sdk.errors.ConnectionException;
import in.stampmitra.sdk.errors.RateLimitException;
import in.stampmitra.sdk.errors.RequestTimeoutException;
import in.stampmitra.sdk.errors.UpstreamException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.http.TransportRequest;
import in.stampmitra.sdk.requests.CreateOrderRequest;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RetryTest {

    private static final String ERR = "{\"error\":{\"code\":\"X\",\"message\":\"m\",\"requestId\":\"r\"}}";
    private static final Pattern UUID_V4 =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private final List<Duration> sleeps = TestClients.sleeps();

    private StampMitra client(FakeTransport t) {
        return TestClients.builder(t, sleeps).build();
    }

    private static java.util.Map<String, Object> duty() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("stateCode", "MH");
        m.put("articleCode", "5");
        m.put("stampDutyAmount", 100);
        return m;
    }

    private static CreateOrderRequest order() {
        return CreateOrderRequest.builder().externalOrderId("o1").externalCustomerId("c1").serviceCode("svc").build();
    }

    @Test
    void getRetriesRetryableStatusesAndSucceeds() {
        FakeTransport t = new FakeTransport().then(503, ERR).then(502, ERR).then(504, ERR).thenOk("{\"ok\":true}");
        StampMitra c = TestClients.builder(t, sleeps).maxRetries(5).build();
        assertThat(c.usage().summary().get("ok")).isEqualTo(true);
        assertThat(t.requests()).hasSize(4);
        assertThat(sleeps).hasSize(3);
    }

    @Test
    void defaultIsTwoRetries() {
        FakeTransport t = new FakeTransport().then(503, ERR);
        assertThatThrownBy(() -> client(t).usage().summary()).isInstanceOf(UpstreamException.class);
        assertThat(t.requests()).hasSize(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void maxRetriesZeroMeansOneAttempt() {
        FakeTransport t = new FakeTransport().then(503, ERR).thenOk("{}");
        assertThatThrownBy(() -> TestClients.builder(t, sleeps).maxRetries(0).build().usage().summary())
                .isInstanceOf(UpstreamException.class);
        assertThat(t.requests()).hasSize(1);
    }

    @Test
    void perRequestMaxRetriesOverridesTheClientDefault() {
        FakeTransport t = new FakeTransport().then(503, ERR);
        assertThatThrownBy(() -> client(t).usage().summary(RequestOptions.builder().maxRetries(4).build()))
                .isInstanceOf(UpstreamException.class);
        assertThat(t.requests()).hasSize(5);
    }

    @Test
    void clientErrorsAreNeverRetried() {
        for (int status : new int[] {400, 401, 402, 403, 404, 409, 500}) {
            FakeTransport t = new FakeTransport().then(status, ERR).thenOk("{}");
            assertThatThrownBy(() -> client(t).usage().summary());
            assertThat(t.requests()).as("status %d", status).hasSize(1);
        }
    }

    @Test
    void backoffIsFullJitterBoundedByTheCap() {
        FakeTransport t = new FakeTransport().then(503, ERR);
        StampMitra c = TestClients.builder(t, sleeps).maxRetries(6).build();
        assertThatThrownBy(() -> c.usage().summary());
        assertThat(sleeps).hasSize(6);
        double[] ceilings = {0.5, 1, 2, 4, 8, 8};
        for (int i = 0; i < 6; i++) {
            assertThat(sleeps.get(i).toNanos() / 1e9).isBetween(0.0, ceilings[i]);
        }
    }

    @Test
    void retryAfterOverridesBackoffAndIsCapped() {
        FakeTransport t = new FakeTransport().then(429, ERR, "Retry-After", "2").then(429, ERR, "Retry-After", "3600")
                .thenOk("{}");
        client(t).usage().summary();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(60));
    }

    @Test
    void retryAfterAsHttpDateIsHonoured() {
        String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(30));
        FakeTransport t = new FakeTransport().then(503, ERR, "Retry-After", date).thenOk("{}");
        client(t).usage().summary();
        assertThat(sleeps.get(0).getSeconds()).isBetween(25L, 31L);
    }

    @Test
    void garbageRetryAfterFallsBackToBackoff() {
        FakeTransport t = new FakeTransport().then(503, ERR, "Retry-After", "soon").thenOk("{}");
        client(t).usage().summary();
        assertThat(sleeps.get(0).toMillis()).isBetween(0L, 500L);
    }

    @Test
    void rateLimitWithoutRetryAfterUsesBackoff() {
        FakeTransport t = new FakeTransport().then(429, ERR).then(429, ERR);
        assertThatThrownBy(() -> TestClients.builder(t, sleeps).maxRetries(1).build().usage().summary())
                .isInstanceOf(RateLimitException.class)
                .satisfies(e -> assertThat(((RateLimitException) e).getRetryAfterSeconds()).isNull());
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0).toMillis()).isBetween(0L, 500L);
    }

    @Test
    void connectionErrorsAndTimeoutsAreRetriedForGet() {
        FakeTransport t = new FakeTransport().thenThrow(new ConnectException("refused"))
                .thenThrow(new HttpTimeoutException("slow")).thenOk("{}");
        client(t).usage().summary();
        assertThat(t.requests()).hasSize(3);

        FakeTransport timeouts = new FakeTransport().thenThrow(new HttpTimeoutException("slow"));
        assertThatThrownBy(() -> client(timeouts).usage().summary()).isInstanceOf(RequestTimeoutException.class);
        assertThat(timeouts.requests()).hasSize(3);

        FakeTransport refused = new FakeTransport().thenThrow(new ConnectException("refused"));
        assertThatThrownBy(() -> client(refused).usage().summary()).isInstanceOf(ConnectionException.class);
        assertThat(refused.requests()).hasSize(3);
    }

    @Test
    void interruptionIsNotRetried() {
        FakeTransport t = new FakeTransport().thenThrow(new java.io.InterruptedIOException("interrupted"));
        assertThatThrownBy(() -> client(t).usage().summary()).isInstanceOf(ConnectionException.class);
        assertThat(t.requests()).hasSize(1);
        Thread.interrupted();
    }

    @Test
    void interruptDuringBackoffStopsRetrying() {
        FakeTransport t = new FakeTransport().then(503, ERR);
        StampMitra c = StampMitra.builder().apiKey(TestClients.KEY).transport(t).sleeper(d -> {
            throw new InterruptedException("stop");
        }).build();
        assertThatThrownBy(() -> c.usage().summary()).isInstanceOf(ConnectionException.class)
                .satisfies(e -> assertThat(((ConnectionException) e).getErrorCode()).isEqualTo("INTERRUPTED"));
        assertThat(Thread.interrupted()).isTrue();
        assertThat(t.requests()).hasSize(1);
    }

    // ---- idempotency --------------------------------------------------------------------------------------

    @Test
    void everyPostGetsAUuidV4KeyExceptWebhookReplay() {
        FakeTransport t = new FakeTransport().thenOk("{}");
        StampMitra c = client(t);
        c.verify().pan("ABCDE1234F");
        assertThat(t.last().getHeaders().get("Idempotency-Key")).matches(UUID_V4);
        c.orders().create(order());
        assertThat(t.last().getHeaders().get("Idempotency-Key")).matches(UUID_V4);
        c.estamp().calculateDuty(duty());
        assertThat(t.last().getHeaders()).containsKey("Idempotency-Key");
        c.estamp().addParties("o1", java.util.Collections.<String, Object>emptyMap());
        assertThat(t.last().getHeaders()).containsKey("Idempotency-Key");
        c.webhooks().events().replay("evt_1");
        assertThat(t.last().getHeaders()).doesNotContainKey("Idempotency-Key");
        c.usage().summary();
        assertThat(t.last().getHeaders()).doesNotContainKey("Idempotency-Key");
    }

    @Test
    void keysAreUniquePerCall() {
        FakeTransport t = new FakeTransport().thenOk("{}");
        StampMitra c = client(t);
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            c.verify().pan("ABCDE1234F");
            keys.add(t.last().getHeaders().get("Idempotency-Key"));
        }
        assertThat(keys).hasSize(50);
    }

    @Test
    void theSameKeyIsReusedAcrossRetries() {
        FakeTransport t = new FakeTransport().then(503, ERR).then(429, ERR).thenOk("{}");
        client(t).verify().pan("ABCDE1234F");
        assertThat(t.requests()).hasSize(3);
        Set<String> keys = new HashSet<>();
        for (TransportRequest r : t.requests()) {
            keys.add(r.getHeaders().get("Idempotency-Key"));
        }
        assertThat(keys).hasSize(1).doesNotContainNull();
    }

    @Test
    void callerKeyIsUsedForEveryAttempt() {
        FakeTransport t = new FakeTransport().then(503, ERR).thenOk("{}");
        client(t).orders().create(order(), RequestOptions.withIdempotencyKey("mine-1"));
        assertThat(t.requests()).extracting(r -> r.getHeaders().get("Idempotency-Key")).containsExactly("mine-1", "mine-1");
    }

    @Test
    void postWithoutAKeyIsNeverRetried() {
        FakeTransport t = new FakeTransport().then(503, ERR).thenOk("{}");
        assertThatThrownBy(() -> client(t).webhooks().events().replay("evt_1")).isInstanceOf(UpstreamException.class);
        assertThat(t.requests()).hasSize(1);

        FakeTransport withKey = new FakeTransport().then(503, ERR).thenOk("{}");
        client(withKey).webhooks().events().replay("evt_1", RequestOptions.withIdempotencyKey("k"));
        assertThat(withKey.requests()).hasSize(2);
    }

    @Test
    void postConflictAndValidationAreNotRetried() {
        FakeTransport t = new FakeTransport().then(409, ERR).thenOk("{}");
        assertThatThrownBy(() -> client(t).orders().create(order())).isInstanceOf(ConflictException.class);
        assertThat(t.requests()).hasSize(1);
        FakeTransport bad = new FakeTransport().then(400, ERR).thenOk("{}");
        assertThatThrownBy(() -> client(bad).orders().create(order())).isInstanceOf(ValidationException.class);
        assertThat(bad.requests()).hasSize(1);
    }

    @Test
    void postRetriesConnectionErrorsAndTimeoutsWhenAKeyIsPresent() {
        FakeTransport t = new FakeTransport().thenThrow(new HttpTimeoutException("slow"))
                .thenThrow(new IOException("reset")).thenOk("{}");
        client(t).orders().create(order());
        assertThat(t.requests()).hasSize(3);
    }

    @Test
    void invalidIdempotencyKeysAreRejectedLocally() {
        FakeTransport t = new FakeTransport().thenOk("{}");
        for (String key : new String[] {"", "a b", "k\r\nX: y", "é", new String(new char[300]).replace('\0', 'a')}) {
            assertThatThrownBy(() -> client(t).orders().create(order(), RequestOptions.withIdempotencyKey(key)))
                    .isInstanceOf(ValidationException.class);
        }
        assertThat(t.requests()).isEmpty();
    }
}
