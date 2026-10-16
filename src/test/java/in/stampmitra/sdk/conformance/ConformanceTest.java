package in.stampmitra.sdk.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.RequestOptions;
import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.Validators;
import in.stampmitra.sdk.Webhook;
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
import in.stampmitra.sdk.http.JdkHttpTransport;
import in.stampmitra.sdk.internal.Json;
import in.stampmitra.sdk.internal.Operation;
import in.stampmitra.sdk.internal.RequestExecutor;
import in.stampmitra.sdk.internal.Secret;
import in.stampmitra.sdk.models.GenericResponse;
import in.stampmitra.sdk.models.LogEntry;
import in.stampmitra.sdk.models.Model;
import in.stampmitra.sdk.models.PanVerification;
import in.stampmitra.sdk.models.WebhookEvent;
import in.stampmitra.sdk.models.WebhookEventType;
import in.stampmitra.sdk.requests.CreateOrderRequest;
import in.stampmitra.sdk.requests.CreatePaymentIntentRequest;
import in.stampmitra.sdk.requests.EstampDutyCalculateRequest;
import in.stampmitra.sdk.requests.EstampPartiesRequest;
import in.stampmitra.sdk.resources.LogsParams;
import in.stampmitra.sdk.support.ScriptedServer;
import in.stampmitra.sdk.support.ScriptedServer.Step;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Black-box conformance run over real HTTP against the spec repo's mock server
 * ({@code stampmitra-developer-openapi/scripts/mock-server.mjs}, started by {@link MockServerExtension}).
 * Skipped when the spec repo or Node is missing, unless {@code CONFORMANCE_REQUIRED=1} (CI), where it fails.
 */
@Tag("conformance")
@ExtendWith(MockServerExtension.class)
class ConformanceTest {

    private static final String KEY = "sk_test_abcdefgh12345678";
    private static final Pattern UUID_V4 =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private MockServer mock;
    private final List<Duration> sleeps = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp(MockServer server) {
        this.mock = server;
        mock.reset();
        sleeps.clear();
    }

    private StampMitra.Builder builder(String baseUrl) {
        return StampMitra.builder().apiKey(KEY).baseUrl(baseUrl).sleeper(sleeps::add);
    }

    private StampMitra client() {
        return builder(mock.baseUrl()).build();
    }

    private static RequestOptions scenario(String s) {
        return RequestOptions.builder().header("x-mock-scenario", s).build();
    }

    // ---------------------------------------------------------------- 31 operations

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** One SDK call: its description, the call, and the HTTP operation it must hit. */
    private static final class Op {
        final String name;
        final Function<StampMitra, Model> call;
        final String method;
        final String template;
        final String path;

        Op(String name, Function<StampMitra, Model> call, String method, String template, String path) {
            this.name = name;
            this.call = call;
            this.method = method;
            this.template = template;
            this.path = path;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static List<Op> operations() {
        String svc = "estamp.instrument.rent_agreement";
        CreateOrderRequest order = CreateOrderRequest.builder().externalOrderId("o1").externalCustomerId("c1")
                .serviceCode(svc).build();
        List<Op> ops = new ArrayList<>();
        ops.add(new Op("verify.pan", c -> c.verify().pan("ABCDE1234F", "Rahul Kumar"), "POST", "/v1/verify/pan", "/v1/verify/pan"));
        ops.add(new Op("verify.gstin", c -> c.verify().gstin("27ABCDE1234F1Z5"), "POST", "/v1/verify/gstin", "/v1/verify/gstin"));
        ops.add(new Op("verify.rc", c -> c.verify().rc("MH12AB1234"), "POST", "/v1/verify/rc", "/v1/verify/rc"));
        ops.add(new Op("verify.voterId", c -> c.verify().voterId("ABC1234567"), "POST", "/v1/verify/voter-id", "/v1/verify/voter-id"));
        ops.add(new Op("verify.drivingLicense", c -> c.verify().drivingLicense("MH1420110012345", "1990-01-31"), "POST", "/v1/verify/driving-license", "/v1/verify/driving-license"));
        ops.add(new Op("verify.passport", c -> c.verify().passport("PA1079341954215", "1990-01-31"), "POST", "/v1/verify/passport", "/v1/verify/passport"));
        ops.add(new Op("verify.cin", c -> c.verify().cin("U74999DL2015PTC284009"), "POST", "/v1/verify/cin", "/v1/verify/cin"));
        ops.add(new Op("verify.udyam", c -> c.verify().udyam("UDYAM-MH-12-1234567"), "POST", "/v1/verify/udyam", "/v1/verify/udyam"));
        ops.add(new Op("verify.panToGstin", c -> c.verify().panToGstin("ABCDE1234F"), "POST", "/v1/verify/pan-to-gstin", "/v1/verify/pan-to-gstin"));
        ops.add(new Op("verify.panToUdyam", c -> c.verify().panToUdyam("ABCDE1234F"), "POST", "/v1/verify/pan-to-udyam", "/v1/verify/pan-to-udyam"));
        ops.add(new Op("verify.bankAccount", c -> c.verify().bankAccount("000123456789", "ICIC0001234", null, "9876543210"), "POST", "/v1/verify/bank-account", "/v1/verify/bank-account"));
        ops.add(new Op("services.list", c -> c.services().list(), "GET", "/v1/services", "/v1/services"));
        ops.add(new Op("services.get", c -> c.services().get(svc), "GET", "/v1/services/{serviceCode}", "/v1/services/" + svc));
        ops.add(new Op("estamp.getSchema", c -> c.estamp().getSchema(svc), "GET", "/v1/estamp/schema/{serviceCode}", "/v1/estamp/schema/" + svc));
        ops.add(new Op("estamp.listStates", c -> c.estamp().listStates(), "GET", "/v1/estamp/states", "/v1/estamp/states"));
        ops.add(new Op("estamp.listInstruments", c -> c.estamp().listInstruments("MH"), "GET", "/v1/estamp/states/{stateCode}/instruments", "/v1/estamp/states/MH/instruments"));
        ops.add(new Op("estamp.listArticles", c -> c.estamp().listArticles("rent_agreement"), "GET", "/v1/estamp/instruments/{instrumentCode}/articles", "/v1/estamp/instruments/rent_agreement/articles"));
        ops.add(new Op("estamp.calculateDuty", c -> c.estamp().calculateDuty(EstampDutyCalculateRequest.builder().stateCode("MH").articleCode("5").stampDutyAmount(100).build()), "POST", "/v1/estamp/duty/calculate", "/v1/estamp/duty/calculate"));
        ops.add(new Op("estamp.addParties", c -> c.estamp().addParties("ord_123", EstampPartiesRequest.builder().customerName("A").partyName("B").build()), "POST", "/v1/estamp/orders/{orderId}/parties", "/v1/estamp/orders/ord_123/parties"));
        ops.add(new Op("orders.create", c -> c.orders().create(order), "POST", "/v1/orders", "/v1/orders"));
        ops.add(new Op("orders.get", c -> c.orders().get("o1"), "GET", "/v1/orders/{id}", "/v1/orders/o1"));
        ops.add(new Op("orders.getStatus", c -> c.orders().getStatus("o1"), "GET", "/v1/orders/{id}/status", "/v1/orders/o1/status"));
        ops.add(new Op("orders.getPayment", c -> c.orders().getPayment("o1"), "GET", "/v1/orders/{id}/payment", "/v1/orders/o1/payment"));
        ops.add(new Op("orders.listDocuments", c -> c.orders().listDocuments("o1"), "GET", "/v1/orders/{id}/documents", "/v1/orders/o1/documents"));
        ops.add(new Op("orders.getDocumentDownload", c -> c.orders().getDocumentDownload("o1", "doc_1"), "GET", "/v1/orders/{id}/documents/{documentId}/download", "/v1/orders/o1/documents/doc_1/download"));
        ops.add(new Op("payments.intents.create", c -> c.payments().intents().create(CreatePaymentIntentRequest.builder().externalOrderId("o1").amountMinor(50000).currency("INR").paymentConnectionId("pc_1").build()), "POST", "/v1/payments/intents", "/v1/payments/intents"));
        ops.add(new Op("payments.intents.get", c -> c.payments().intents().get("pi_1"), "GET", "/v1/payments/intents/{id}", "/v1/payments/intents/pi_1"));
        ops.add(new Op("usage.summary", c -> c.usage().summary(), "GET", "/v1/usage/summary", "/v1/usage/summary"));
        ops.add(new Op("logs.list", c -> c.logs().list(LogsParams.builder().limit(50).build()), "GET", "/v1/logs", "/v1/logs"));
        ops.add(new Op("webhooks.events.list", c -> c.webhooks().events().list(20, null), "GET", "/v1/webhooks/events", "/v1/webhooks/events"));
        ops.add(new Op("webhooks.events.replay", c -> c.webhooks().events().replay("evt_1"), "POST", "/v1/webhooks/events/{id}/replay", "/v1/webhooks/events/evt_1/replay"));
        return ops;
    }

    static Stream<Arguments> operationArgs() {
        return operations().stream().map(Arguments::of);
    }

    @Test
    void thereAreExactlyThirtyOneOperations() {
        assertThat(operations()).hasSize(31);
        assertThat(operations().stream().map(o -> o.method + " " + o.template).distinct().count()).isEqualTo(31);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operationArgs")
    void everyOperationHitsTheRightRouteAndPreservesTheWholeResponse(Op op) throws IOException {
        Model result = op.call.apply(client());
        assertThat(result).isNotNull();
        assertThat(result.getResponse().getStatus()).isBetween(200, 299);

        JsonNode seen = mock.requests();
        assertThat(seen).hasSize(1);
        JsonNode r = seen.get(0);
        assertThat(r.get("method").asText()).isEqualTo(op.method);
        assertThat(r.get("path").asText()).isEqualTo(op.path);
        assertThat(r.get("headers").get("authorization").asText()).isEqualTo("Bearer " + KEY);
        assertThat(r.get("headers").get("user-agent").asText()).startsWith("stampmitra-java/0.1.0 (Java ");
        assertThat(r.get("headers").get("accept").asText()).isEqualTo("application/json");
        if (op.method.equals("POST") && !op.template.endsWith("/replay")) {
            assertThat(r.get("headers").get("idempotency-key").asText()).matches(UUID_V4);
        } else {
            assertThat(r.get("headers").has("idempotency-key")).isFalse();
        }

        // Unknown fields are preserved: the model's raw body equals the documented fixture body.
        SpecFixtures.Fixture fx = SpecFixtures.success(SpecFixtures.load(mock.specDir()), op.method, op.template);
        JsonNode expected = fx.body.isArray() ? Json.mapper().createObjectNode().set("items", fx.body) : fx.body;
        JsonNode actual = Json.mapper().valueToTree(result.getRaw());
        assertThat(actual.equals(Comparator.comparingDouble(JsonNode::asDouble), expected))
                .as("raw body of %s equals fixture %s", op.name, fx.file).isTrue();
    }

    @Test
    void requestBodiesCarryTheDocumentedFields() {
        client().verify().bankAccount("000123456789", "ICIC0001234", "Rahul Kumar", "9876543210");
        client().orders().create(CreateOrderRequest.builder().externalOrderId("o1").externalCustomerId("c1")
                .serviceCode("s").details(map("rent", 25000)).build());
        JsonNode seen = mock.requests();
        assertThat(seen.get(0).get("body").toString()).isEqualTo(
                "{\"accountNumber\":\"000123456789\",\"ifsc\":\"ICIC0001234\",\"name\":\"Rahul Kumar\",\"phone\":\"9876543210\"}");
        assertThat(seen.get(1).get("body").get("details").get("rent").asInt()).isEqualTo(25000);
    }

    // ---------------------------------------------------------------- error mapping

    static Stream<Arguments> errorStatuses() {
        return Stream.of(
                Arguments.of(400, ValidationException.class), Arguments.of(401, AuthenticationException.class),
                Arguments.of(402, PaymentRequiredException.class), Arguments.of(403, PermissionException.class),
                Arguments.of(404, NotFoundException.class), Arguments.of(409, ConflictException.class),
                Arguments.of(429, RateLimitException.class), Arguments.of(500, UpstreamException.class),
                Arguments.of(502, UpstreamException.class), Arguments.of(503, UpstreamException.class),
                Arguments.of(504, UpstreamException.class));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @MethodSource("errorStatuses")
    void errorStatusMapping(int status, Class<? extends StampMitraException> type) {
        StampMitra c = builder(mock.baseUrl()).maxRetries(0).build();
        assertThatThrownBy(() -> c.usage().summary(scenario("error-" + status))).isExactlyInstanceOf(type).satisfies(e -> {
            StampMitraException ex = (StampMitraException) e;
            assertThat(ex.getStatus()).isEqualTo(status);
            assertThat(ex.getErrorCode()).isNotEmpty();
            assertThat(ex.getRequestId()).isNotEmpty();
            assertThat(ex.getMessage()).isNotEmpty();
            assertThat(ex.toString()).doesNotContain(KEY);
        });
    }

    static Stream<SpecFixtures.Fixture> errorFixtures() throws IOException {
        return SpecFixtures.load(MockServerExtension.specDir()).stream().filter(f -> f.scenario.startsWith("error-"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorFixtures")
    void everyDocumentedErrorFixtureMapsByStatusAndKeepsCodeMessageAndRequestId(SpecFixtures.Fixture fx) throws IOException {
        RequestExecutor executor = executor(mock.baseUrl(), 0, Duration.ofSeconds(5));
        String path = fx.request.get("path").asText();
        boolean post = fx.method.equals("POST");
        Object body = post ? Json.mapper().convertValue(fx.request.get("body"), Object.class) : null;
        assertThatThrownBy(() -> executor.execute(post ? Operation.post(path) : Operation.get(path), path, null, body,
                RequestOptions.builder().header("x-mock-scenario", fx.scenario).build(), post, GenericResponse::new))
                .isInstanceOf(StampMitraException.class).satisfies(e -> {
                    StampMitraException ex = (StampMitraException) e;
                    assertThat(ex.getStatus()).isEqualTo(fx.status);
                    JsonNode err = fx.body.get("error");
                    assertThat(ex.getErrorCode()).isEqualTo(err.get("code").asText());
                    assertThat(ex.getMessage()).isEqualTo(err.get("message").asText());
                    assertThat(ex.getRequestId()).isEqualTo(err.get("requestId").asText());
                    if (fx.status == 429 && !fx.request.toString().isEmpty()) {
                        assertThat(e).isInstanceOf(RateLimitException.class);
                    }
                });
    }

    @Test
    void rateLimitWithRetryAfterHeader() {
        StampMitra c = builder(mock.baseUrl()).maxRetries(0).build();
        assertThatThrownBy(() -> c.usage().summary(scenario("retry-after:7"))).isInstanceOf(RateLimitException.class)
                .satisfies(e -> assertThat(((RateLimitException) e).getRetryAfterSeconds()).isEqualTo(7.0));
    }

    @Test
    void retryAfterOverridesBackoffAndIsCapped() {
        client().usage().summary(scenario("seq:ra:503,200"));
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isLessThanOrEqualTo(Duration.ofMillis(500));

        mock.reset();
        sleeps.clear();
        StampMitra one = builder(mock.baseUrl()).maxRetries(1).build();
        assertThatThrownBy(() -> one.usage().summary(scenario("retry-after:3"))).isInstanceOf(RateLimitException.class);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
        assertThat(mock.requests()).hasSize(2);

        mock.reset();
        sleeps.clear();
        assertThatThrownBy(() -> one.usage().summary(scenario("retry-after:3600"))).isInstanceOf(RateLimitException.class);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void rateLimitWithoutRetryAfterUsesTheComputedBackoff() {
        // GET /v1/estamp/states documents a 429 that carries no Retry-After header.
        StampMitra one = builder(mock.baseUrl()).maxRetries(1).build();
        assertThatThrownBy(() -> one.estamp().listStates(scenario("error-429"))).isInstanceOf(RateLimitException.class)
                .satisfies(e -> assertThat(((RateLimitException) e).getRetryAfterSeconds()).isNull());
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isLessThanOrEqualTo(Duration.ofMillis(500));
    }

    // ---------------------------------------------------------------- shared retry scenarios

    private RequestExecutor executor(String baseUrl, int maxRetries, Duration timeout) {
        return new RequestExecutor(new RequestExecutor.Config(new Secret(KEY), baseUrl, new JdkHttpTransport(), timeout,
                maxRetries, Collections.<String, String>emptyMap(), "stampmitra-java-conformance", null, sleeps::add,
                Runnable::run));
    }

    static Stream<Arguments> retryScenarios() throws IOException {
        JsonNode file = Json.mapper().readTree(ConformanceTest.class.getResourceAsStream("/fixtures/retry-scenarios.json"));
        List<Arguments> out = new ArrayList<>();
        for (JsonNode s : file.get("scenarios")) {
            out.add(Arguments.of(s.get("name").asText(), s));
        }
        return out.stream();
    }

    private static Step stepFor(JsonNode item) {
        if (item.isTextual()) {
            return item.asText().equals("connection_error") ? Step.drop() : Step.slow(1500, 200, "{}");
        }
        if (item.isInt()) {
            return Step.json(item.asInt(), item.asInt() < 300 ? "{\"ok\":true}"
                    : "{\"error\":{\"code\":\"X\",\"message\":\"scripted\",\"requestId\":\"req-s\"}}");
        }
        Step step = Step.json(item.get("status").asInt(), item.has("body") ? item.get("body").toString()
                : item.get("status").asInt() < 300 ? "{\"ok\":true}"
                : "{\"error\":{\"code\":\"X\",\"message\":\"scripted\",\"requestId\":\"req-s\"}}");
        if (item.has("headers")) {
            item.get("headers").properties().forEach(h -> step.header(h.getKey(), h.getValue().asText()));
        }
        return step;
    }

    private static Class<? extends Throwable> errorClass(String name) {
        switch (name) {
            case "ValidationError": return ValidationException.class;
            case "AuthenticationError": return AuthenticationException.class;
            case "PermissionError": return PermissionException.class;
            case "NotFoundError": return NotFoundException.class;
            case "ConflictError": return ConflictException.class;
            case "RateLimitError": return RateLimitException.class;
            case "UpstreamError": return UpstreamException.class;
            case "TimeoutError": return RequestTimeoutException.class;
            case "ConnectionError": return ConnectionException.class;
            default: throw new IllegalArgumentException(name);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("retryScenarios")
    void sharedRetryScenario(String name, JsonNode s) throws Exception {
        List<Step> steps = new ArrayList<>();
        for (JsonNode item : s.get("sequence")) {
            steps.add(stepFor(item));
        }
        boolean optional = s.path("optional").asBoolean(false);
        String method = s.get("method").asText();
        String path = s.get("path").asText();
        int maxRetries = s.has("maxRetries") ? s.get("maxRetries").asInt() : 2;
        String keyMode = s.get("idempotencyKey").isTextual() ? s.get("idempotencyKey").asText()
                : s.get("idempotencyKey").asBoolean() ? "caller" : "none";

        try (ScriptedServer server = new ScriptedServer(steps.toArray(new Step[0]))) {
            RequestExecutor executor = executor(server.url(), maxRetries, Duration.ofMillis(400));
            RequestOptions.Builder options = RequestOptions.builder();
            if (keyMode.equals("caller")) {
                options.idempotencyKey("caller-key-1");
            }
            boolean post = method.equals("POST");
            Optional<StampMitraException> thrown = Optional.empty();
            Integer status = null;
            try {
                GenericResponse r = executor.execute(post ? Operation.post(path) : Operation.get(path), path, null,
                        post ? Collections.<String, Object>emptyMap() : null, options.build(), keyMode.equals("auto"),
                        GenericResponse::new);
                status = r.getResponse().getStatus();
            } catch (StampMitraException e) {
                thrown = Optional.of(e);
            }

            JsonNode outcome = s.get("outcome");
            int expectedAttempts = s.get("expectedAttempts").asInt();
            if (optional) {
                expectedAttempts = 1; // a keyless verification POST must not be retried by an SDK that cannot prove "no bytes sent"
                assertThat(thrown).isPresent();
            } else if (outcome.isInt()) {
                assertThat(thrown).as("unexpected error: %s", thrown.map(Throwable::getMessage).orElse("")).isEmpty();
                assertThat(status).isBetween(200, 299);
            } else {
                assertThat(thrown).as("expected %s", outcome.asText()).isPresent();
                String cls = outcome.asText().replaceAll("\\(.*$", "");
                assertThat(thrown.get()).isExactlyInstanceOf(errorClass(cls));
                java.util.regex.Matcher m = Pattern.compile("\\((?:retryAfter=)?(\\d{1,3})\\)").matcher(outcome.asText());
                if (m.find() && outcome.asText().contains("Upstream")) {
                    assertThat(thrown.get().getStatus()).isEqualTo(Integer.parseInt(m.group(1)));
                }
                if (outcome.asText().contains("retryAfter=")) {
                    assertThat(((RateLimitException) thrown.get()).getRetryAfterSeconds())
                            .isEqualTo(Double.parseDouble(outcome.asText().replaceAll(".*retryAfter=(\\d+).*", "$1")));
                }
            }

            assertThat(server.requests()).as(name).hasSize(expectedAttempts);
            if (s.path("sameIdempotencyKeyAcrossAttempts").asBoolean(false)) {
                List<String> keys = server.requests().stream().map(r -> r.headers.get("idempotency-key")).distinct()
                        .collect(Collectors.toList());
                assertThat(keys).hasSize(1);
                assertThat(keys.get(0)).isNotEmpty();
                if (keyMode.equals("auto")) {
                    assertThat(keys.get(0)).matches(UUID_V4);
                } else {
                    assertThat(keys.get(0)).isEqualTo("caller-key-1");
                }
            }
            if (keyMode.equals("none")) {
                server.requests().forEach(r -> assertThat(r.headers).doesNotContainKey("idempotency-key"));
            }
            // (The JDK HttpClient may transparently re-send an idempotent GET once after a dropped connection, so
            // for connection_error steps the server can see more requests than the SDK made attempts.)
            if (s.has("expectedDelayBeforeAttempt2Seconds")) {
                assertThat(sleeps.get(0)).isEqualTo(Duration.ofSeconds(s.get("expectedDelayBeforeAttempt2Seconds").asLong()));
            }
        }
    }

    @Test
    void mockSequencesDriveTheSameBehaviourThroughTheResourceApi() {
        // seq:<key>:<statuses>: 503, 503, then the success fixture (GET is always retry-safe)
        client().services().list(scenario("seq:s1:503,503,200"));
        assertThat(mock.requests()).hasSize(3);
        mock.reset();
        assertThatThrownBy(() -> client().usage().summary(scenario("seq:s2:503,503,503,200"))).isInstanceOf(UpstreamException.class);
        assertThat(mock.requests()).hasSize(3);
        mock.reset();
        assertThatThrownBy(() -> client().usage().summary(scenario("seq:s3:404,200"))).isInstanceOf(NotFoundException.class);
        assertThat(mock.requests()).hasSize(1);
        mock.reset();
        StampMitra five = builder(mock.baseUrl()).maxRetries(5).build();
        five.services().list(scenario("seq:s4:503,503,503,503,503,200"));
        assertThat(mock.requests()).hasSize(6);
    }

    @Test
    void idempotencyKeyIsReusedAcrossRetriesViaResourceMethods() {
        client().verify().pan("ABCDE1234F", null, scenario("seq:idem:503,502,200"));
        JsonNode reqs = mock.requests();
        assertThat(reqs).hasSize(3);
        assertThat(reqs.findValuesAsText("idempotency-key")).hasSize(3).containsOnly(reqs.get(0).get("headers").get("idempotency-key").asText());

        mock.reset();
        RequestOptions o = RequestOptions.builder().idempotencyKey("mine-1").header("x-mock-scenario", "seq:idem2:503,200").build();
        client().orders().create(CreateOrderRequest.builder().externalOrderId("o").externalCustomerId("c").serviceCode("s").build(), o);
        assertThat(mock.requests().findValuesAsText("idempotency-key")).containsExactly("mine-1", "mine-1");
    }

    // ---------------------------------------------------------------- transport failures

    @Test
    void timeoutRaisesATimeoutErrorAndHonoursThePerCallOverride() {
        StampMitra c = builder(mock.baseUrl()).maxRetries(0).timeout(Duration.ofSeconds(30)).build();
        RequestOptions o = RequestOptions.builder().timeout(Duration.ofMillis(300)).header("x-mock-scenario", "delay:1500").build();
        long start = System.nanoTime();
        assertThatThrownBy(() -> c.usage().summary(o)).isExactlyInstanceOf(RequestTimeoutException.class)
                .isInstanceOf(ConnectionException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1300));
    }

    @Test
    void timeoutsAreRetriedForGetThenRaised() {
        StampMitra c = builder(mock.baseUrl()).maxRetries(1).build();
        RequestOptions o = RequestOptions.builder().timeout(Duration.ofMillis(200)).header("x-mock-scenario", "delay:1000").build();
        assertThatThrownBy(() -> c.usage().summary(o)).isInstanceOf(RequestTimeoutException.class);
        assertThat(mock.requests()).hasSize(2);
    }

    @Test
    void connectionRefused() {
        StampMitra c = builder("http://127.0.0.1:1").maxRetries(1).build();
        assertThatThrownBy(() -> c.usage().summary()).isExactlyInstanceOf(ConnectionException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY));
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void connectionDroppedMidRequestIsRetriedForGet() {
        client().usage().summary(scenario("seq:drop:conn,200"));
        assertThat(mock.requests().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void droppedConnectionOnPostWithKeyIsRetriedAndWithoutKeyIsNot() {
        client().verify().pan("ABCDE1234F", null, scenario("seq:dropp:conn,200"));
        assertThat(mock.requests().size()).isGreaterThanOrEqualTo(2);
        mock.reset();
        assertThatThrownBy(() -> client().webhooks().events().replay("evt_1", scenario("seq:dropr:conn,200")))
                .isInstanceOf(ConnectionException.class);
    }

    // ---------------------------------------------------------------- bodies the mock cannot produce

    @Test
    void nonJsonErrorBodiesStillMapByStatus() throws IOException {
        Object[][] cases = {{502, UpstreamException.class}, {429, RateLimitException.class}, {404, NotFoundException.class}};
        for (Object[] c : cases) {
            try (ScriptedServer s = new ScriptedServer(Step.status((Integer) c[0], "<html>Bad Gateway</html>").header("Content-Type", "text/html"))) {
                StampMitra client = builder(s.url()).maxRetries(0).build();
                assertThatThrownBy(() -> client.usage().summary()).isExactlyInstanceOf((Class<?>) c[1])
                        .satisfies(e -> assertThat(((StampMitraException) e).getStatus()).isEqualTo(c[0]));
            }
        }
    }

    @Test
    void unknownExtraResponseFieldsAreTolerated() throws IOException {
        String body = "{\"referenceId\":\"SMV-1\",\"valid\":true,\"brandNewField\":{\"x\":1},\"registeredName\":\"ACME\"}";
        try (ScriptedServer s = new ScriptedServer(Step.json(200, body))) {
            PanVerification res = builder(s.url()).build().verify().pan("ABCDE1234F");
            assertThat(res.isValid()).isTrue();
            assertThat(res.get("brandNewField")).isEqualTo(Collections.singletonMap("x", 1));
            assertThat(res.getRaw()).containsKey("brandNewField");
        }
    }

    @Test
    void logsPaginationAcrossThreePages() throws IOException {
        java.util.function.BiFunction<String, String, String> page = (id, next) -> "{\"items\":[{\"id\":\"" + id
                + "\",\"createdAt\":\"2026-10-15T09:41:07.000Z\",\"service\":\"pan.verify\"}],\"nextCursor\":"
                + (next == null ? "null" : "\"" + next + "\"") + "}";
        try (ScriptedServer s = new ScriptedServer(Step.json(200, page.apply("a", "c1")), Step.json(200, page.apply("b", "c2")),
                Step.json(200, page.apply("c", null)))) {
            List<String> ids = new ArrayList<>();
            for (LogEntry e : builder(s.url()).build().logs().iterate(LogsParams.builder().limit(1).build())) {
                ids.add(e.getId());
            }
            assertThat(ids).containsExactly("a", "b", "c");
            assertThat(s.requests()).extracting(r -> r.query).containsExactly("limit=1", "limit=1&cursor=c1", "limit=1&cursor=c2");
        }
    }

    @Test
    void logsPaginationAgainstTheMock() {
        List<String> ids = new ArrayList<>();
        for (LogEntry e : client().logs().iterate(LogsParams.builder().limit(50).build())) {
            ids.add(e.getId());
        }
        assertThat(ids).isNotEmpty();
        assertThat(mock.requests()).hasSize(1); // the fixture ends with nextCursor=null
    }

    // ---------------------------------------------------------------- shared vectors (read from the spec repo)

    private static JsonNode specJson(MockServer mock, String rel) throws IOException {
        return Json.mapper().readTree(Files.readAllBytes(mock.specDir().resolve(rel)));
    }

    @Test
    void vendoredVectorFilesMatchTheSpecRepo() throws IOException {
        for (String f : new String[] {"validators.json", "webhook-signatures.json", "retry-scenarios.json"}) {
            JsonNode vendored = Json.mapper().readTree(ConformanceTest.class.getResourceAsStream("/fixtures/" + f));
            assertThat(vendored).as("src/test/resources/fixtures/%s is stale: copy it from the spec repo", f)
                    .isEqualTo(specJson(mock, "fixtures/" + f));
        }
    }

    @Test
    void bundledSpecMatchesTheSpecRepo() throws IOException {
        JsonNode bundled = Json.mapper().readTree(Files.readAllBytes(java.nio.file.Paths.get(System.getProperty("project.basedir", "."), "spec/openapi.json")));
        assertThat(bundled).as("spec/openapi.json is stale: copy it from the spec repo").isEqualTo(specJson(mock, "openapi.json"));
    }

    @Test
    void webhookSignatureVectorsFromTheSpecRepo() throws IOException {
        JsonNode f = specJson(mock, "fixtures/webhook-signatures.json");
        int checked = 0;
        for (JsonNode v : f.get("vectors")) {
            Optional<String> reason = Webhook.failureReason(v.get("payload").asText().getBytes(StandardCharsets.UTF_8),
                    v.get("timestamp").asText(), v.get("signature").asText(), v.get("secret").asText(),
                    f.get("toleranceSeconds").asLong(), f.get("now").asLong());
            if (v.get("expected").get("valid").asBoolean()) {
                assertThat(reason).as(v.get("name").asText()).isEmpty();
            } else {
                assertThat(reason).as(v.get("name").asText()).contains(v.get("expected").get("reason").asText());
            }
            checked++;
        }
        assertThat(checked).isGreaterThanOrEqualTo(24);
    }

    @Test
    void everyDocumentedWebhookDeliveryVerifiesAndYieldsATypedEvent() throws IOException {
        int n = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(mock.specDir().resolve("fixtures/webhooks"), "*.json")) {
            for (Path p : files) {
                JsonNode d = Json.mapper().readTree(Files.readAllBytes(p));
                JsonNode h = d.get("headers");
                WebhookEvent e = Webhook.constructEvent(d.get("rawBody").asText().getBytes(StandardCharsets.UTF_8),
                        h.get("X-Webhook-Timestamp").asText(), h.get("X-Webhook-Signature").asText(), d.get("secret").asText(),
                        300, h.get("X-Webhook-Event-Id").asText(), d.get("now").asLong());
                assertThat(e.getType()).as(p.getFileName().toString()).isNotEqualTo(WebhookEventType.UNKNOWN);
                assertThat(e.getType().wireName()).isEqualTo(d.get("event").asText());
                assertThat(e.getCoreOrderId()).isNotEmpty();
                assertThat(e.getOccurredAt()).isNotNull();
                assertThat(e.getEventId()).isEqualTo(h.get("X-Webhook-Event-Id").asText());
                n++;
            }
        }
        assertThat(n).isEqualTo(8);
    }

    @Test
    void validatorVectorsFromTheSpecRepo() throws IOException {
        JsonNode all = specJson(mock, "fixtures/validators.json").get("validators");
        Map<String, Function<String, String>> fns = new LinkedHashMap<>();
        fns.put("pan", Validators::pan);
        fns.put("gstin", Validators::gstin);
        fns.put("ifsc", Validators::ifsc);
        fns.put("cin", Validators::cin);
        fns.put("udyam", Validators::udyam);
        fns.put("vehicleNumber", Validators::vehicleNumber);
        fns.put("bankAccount", Validators::bankAccount);
        fns.put("passport", Validators::passportFileNumber);
        fns.put("drivingLicense", Validators::drivingLicense);
        fns.put("voterId", Validators::voterId);
        int n = 0;
        for (Map.Entry<String, Function<String, String>> e : fns.entrySet()) {
            JsonNode def = all.get(e.getKey());
            for (JsonNode ok : def.get("valid")) {
                String out = e.getValue().apply(ok.asText());
                assertThat(out).matches(def.get("pattern").asText());
                n++;
            }
            for (JsonNode bad : def.get("invalid")) {
                try {
                    e.getValue().apply(bad.asText());
                    fail(e.getKey() + " should reject '" + bad.asText() + "'");
                } catch (ValidationException expected) {
                    n++;
                }
            }
        }
        assertThat(n).isGreaterThan(100);
    }
}
