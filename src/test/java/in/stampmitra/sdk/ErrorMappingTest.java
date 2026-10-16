package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.errors.AuthenticationException;
import in.stampmitra.sdk.errors.ConflictException;
import in.stampmitra.sdk.errors.ConnectionException;
import in.stampmitra.sdk.errors.NotFoundException;
import in.stampmitra.sdk.errors.PaymentRequiredException;
import in.stampmitra.sdk.errors.PermissionException;
import in.stampmitra.sdk.errors.RateLimitException;
import in.stampmitra.sdk.errors.RequestTimeoutException;
import in.stampmitra.sdk.errors.StampMitraException;
import in.stampmitra.sdk.errors.UpstreamException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorMappingTest {

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of(400, ValidationException.class), Arguments.of(401, AuthenticationException.class),
                Arguments.of(402, PaymentRequiredException.class), Arguments.of(403, PermissionException.class),
                Arguments.of(404, NotFoundException.class), Arguments.of(409, ConflictException.class),
                Arguments.of(429, RateLimitException.class), Arguments.of(500, UpstreamException.class),
                Arguments.of(502, UpstreamException.class), Arguments.of(503, UpstreamException.class),
                Arguments.of(504, UpstreamException.class), Arguments.of(418, StampMitraException.class),
                Arguments.of(302, StampMitraException.class));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void mapsByStatus(int status, Class<? extends StampMitraException> type) {
        FakeTransport t = new FakeTransport().then(status,
                "{\"error\":{\"code\":\"SOME_CODE\",\"message\":\"Server says no\",\"requestId\":\"req-1\",\"field\":\"pan\"}}");
        StampMitra c = TestClients.builder(t, TestClients.sleeps()).maxRetries(0).build();
        assertThatThrownBy(() -> c.usage().summary()).isExactlyInstanceOf(type).satisfies(e -> {
            StampMitraException ex = (StampMitraException) e;
            assertThat(ex.getStatus()).isEqualTo(status);
            assertThat(ex.getErrorCode()).isEqualTo("SOME_CODE");
            assertThat(ex.getRequestId()).isEqualTo("req-1");
            assertThat(ex.getField()).isEqualTo("pan");
            assertThat(ex.getMessage()).isEqualTo("Server says no");
            assertThat(ex.getBody()).isInstanceOf(Map.class);
        });
    }

    @Test
    void nonJsonErrorBodyStillMapsByStatus() {
        for (int status : new int[] {502, 429, 404}) {
            FakeTransport t = new FakeTransport().then(status, "<html>Bad Gateway</html>", "Content-Type", "text/html");
            StampMitra c = TestClients.builder(t, TestClients.sleeps()).maxRetries(0).build();
            assertThatThrownBy(() -> c.usage().summary()).isInstanceOf(StampMitraException.class).satisfies(e -> {
                StampMitraException ex = (StampMitraException) e;
                assertThat(ex.getStatus()).isEqualTo(status);
                assertThat(ex.getMessage()).isEqualTo("HTTP " + status);
                assertThat(ex.getBody()).isEqualTo("<html>Bad Gateway</html>");
            });
        }
    }

    @Test
    void emptyAndArrayBodiesDoNotBreakMapping() {
        for (String body : new String[] {"", "[]", "null", "\"oops\"", "{\"error\":\"flat message\"}"}) {
            FakeTransport t = new FakeTransport().then(500, body);
            StampMitra c = TestClients.builder(t, TestClients.sleeps()).maxRetries(0).build();
            assertThatThrownBy(() -> c.usage().summary()).isInstanceOf(UpstreamException.class);
        }
        FakeTransport t = new FakeTransport().then(500, "{\"error\":\"flat message\"}");
        assertThatThrownBy(() -> TestClients.builder(t, TestClients.sleeps()).maxRetries(0).build().usage().summary())
                .hasMessage("flat message");
    }

    @Test
    void requestIdFallsBackToTheHeader() {
        FakeTransport t = new FakeTransport().then(404, "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"x\"}}",
                "X-Request-Id", "hdr-9");
        assertThatThrownBy(() -> TestClients.client(t).orders().get("o1")).isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((StampMitraException) e).getRequestId()).isEqualTo("hdr-9"));
    }

    @Test
    void permissionMessageIsKeptVerbatim() {
        String msg = "Passport verification requires an IP-allowlisted API key. Your IP 203.0.113.7 is not allowed.";
        FakeTransport t = new FakeTransport().then(403, "{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"" + msg
                + "\",\"requestId\":\"r\"}}");
        assertThatThrownBy(() -> TestClients.client(t).verify().passport("PA1079341954215", "1990-01-31"))
                .isInstanceOf(PermissionException.class).hasMessage(msg);
    }

    @Test
    void retryAfterIsParsed() {
        FakeTransport t = new FakeTransport().then(429, "{}", "Retry-After", "7");
        assertThatThrownBy(() -> TestClients.builder(t, TestClients.sleeps()).maxRetries(0).build().usage().summary())
                .isInstanceOf(RateLimitException.class)
                .satisfies(e -> assertThat(((RateLimitException) e).getRetryAfterSeconds()).isEqualTo(7.0));
        FakeTransport none = new FakeTransport().then(429, "{}");
        assertThatThrownBy(() -> TestClients.builder(none, TestClients.sleeps()).maxRetries(0).build().usage().summary())
                .satisfies(e -> assertThat(((RateLimitException) e).getRetryAfterSeconds()).isNull());
    }

    @Test
    void anEchoedApiKeyIsScrubbedFromMessagesAndBodies() {
        FakeTransport t = new FakeTransport().then(401,
                "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"bad key " + TestClients.KEY + "\"}}");
        assertThatThrownBy(() -> TestClients.client(t).usage().summary()).satisfies(e -> {
            assertThat(e.getMessage()).doesNotContain(TestClients.KEY).contains("[REDACTED]");
            assertThat(e.toString()).doesNotContain(TestClients.KEY);
        });
        FakeTransport raw = new FakeTransport().then(502, "upstream said " + TestClients.KEY);
        assertThatThrownBy(() -> TestClients.builder(raw, TestClients.sleeps()).maxRetries(0).build().usage().summary())
                .satisfies(e -> assertThat(((StampMitraException) e).getBody().toString()).doesNotContain(TestClients.KEY));
    }

    @Test
    void networkFailuresAreConnectionErrors() {
        assertThatThrownBy(() -> TestClients.builder(new FakeTransport().thenThrow(new ConnectException("refused")),
                TestClients.sleeps()).maxRetries(0).build().usage().summary())
                .isExactlyInstanceOf(ConnectionException.class).hasMessageContaining("refused")
                .satisfies(e -> assertThat(((StampMitraException) e).getStatus()).isNull());
    }

    @Test
    void timeoutsAreTimeoutErrors() {
        for (IOException io : new IOException[] {new HttpTimeoutException("t"), new SocketTimeoutException("t")}) {
            assertThatThrownBy(() -> TestClients.builder(new FakeTransport().thenThrow(io), TestClients.sleeps())
                    .maxRetries(0).build().usage().summary())
                    .isExactlyInstanceOf(RequestTimeoutException.class).isInstanceOf(ConnectionException.class);
        }
    }

    @Test
    void successWithGarbageBodyIsReported() {
        FakeTransport t = new FakeTransport().then(200, "<html>captive portal</html>");
        assertThatThrownBy(() -> TestClients.client(t).usage().summary()).isInstanceOf(StampMitraException.class)
                .hasMessageContaining("non-JSON").satisfies(e -> assertThat(((StampMitraException) e).getErrorCode())
                        .isEqualTo("INVALID_RESPONSE"));
    }

    @Test
    void runtimeFailuresInATransportAreWrappedAndNotRetried() {
        FakeTransport t = new FakeTransport().thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> TestClients.client(t).usage().summary()).isInstanceOf(StampMitraException.class)
                .hasMessageContaining("boom");
        assertThat(t.requests()).hasSize(1);
    }
}
