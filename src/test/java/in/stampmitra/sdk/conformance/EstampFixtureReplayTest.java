package in.stampmitra.sdk.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.RequestOptions;
import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.errors.AuthenticationException;
import in.stampmitra.sdk.errors.ConflictException;
import in.stampmitra.sdk.errors.FieldIssue;
import in.stampmitra.sdk.errors.NotFoundException;
import in.stampmitra.sdk.errors.PaymentRequiredException;
import in.stampmitra.sdk.errors.PermissionException;
import in.stampmitra.sdk.errors.RateLimitException;
import in.stampmitra.sdk.errors.StampMitraException;
import in.stampmitra.sdk.errors.UpstreamException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.internal.Json;
import in.stampmitra.sdk.models.Model;
import in.stampmitra.sdk.requests.DocumentUpload;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Table-driven replay of the spec repo's golden fixtures for the 31 operations added in contract 1.2.0: each fixture's
 * request is rebuilt through the SDK method for that operation and sent to the mock server, then the recorded request
 * (path, query, JSON body or multipart parts, Idempotency-Key) and the decoded response or exception are compared
 * with the fixture.
 */
@Tag("conformance")
@ExtendWith(MockServerExtension.class)
class EstampFixtureReplayTest {

    private static final String KEY = "sk_test_abcdefgh12345678";

    /** One SDK call for a fixture: path parameters (in template order), query, JSON body, multipart parts, options. */
    private interface Invoker {
        Model call(StampMitra c, List<String> p, Map<String, String> q, Map<String, Object> body, JsonNode multipart,
                   RequestOptions o);
    }

    private static final Map<String, Invoker> INVOKERS = new LinkedHashMap<>();

    private static Integer intOf(Map<String, String> q, String k) {
        return q.containsKey(k) ? Integer.valueOf(q.get(k)) : null;
    }

    static {
        INVOKERS.put("GET /v1/estamp/states/availability", (c, p, q, b, m, o) -> c.estamp()
                .getStatesAvailability(q.containsKey("active") ? Boolean.valueOf(q.get("active")) : null, o));
        INVOKERS.put("GET /v1/estamp/states/{stateCode}", (c, p, q, b, m, o) -> c.estamp().getState(p.get(0), o));
        INVOKERS.put("GET /v1/estamp/states/{stateCode}/requirements", (c, p, q, b, m, o) -> c.estamp()
                .getStateRequirements(p.get(0), q.get("articleCode"),
                        q.containsKey("denomination") ? new BigDecimal(q.get("denomination")) : null, q.get("category"), o));
        INVOKERS.put("GET /v1/estamp/states/{stateCode}/districts", (c, p, q, b, m, o) -> c.estamp().listDistricts(p.get(0), o));
        INVOKERS.put("GET /v1/estamp/states/{stateCode}/districts/{districtCode}/sros",
                (c, p, q, b, m, o) -> c.estamp().listSros(p.get(0), p.get(1), o));
        INVOKERS.put("GET /v1/estamp/wallet", (c, p, q, b, m, o) -> c.estamp().getWallet(o));
        INVOKERS.put("POST /v1/estamp/orders/validate", (c, p, q, b, m, o) -> c.estamp().orders().validate(b, o));
        INVOKERS.put("POST /v1/estamp/orders", (c, p, q, b, m, o) -> c.estamp().orders().create(b, o));
        INVOKERS.put("GET /v1/estamp/orders", (c, p, q, b, m, o) -> c.estamp().orders()
                .list(intOf(q, "page"), intOf(q, "limit"), q.get("cursor"), o));
        INVOKERS.put("GET /v1/estamp/orders/{id}", (c, p, q, b, m, o) -> c.estamp().orders().get(p.get(0), o));
        INVOKERS.put("POST /v1/estamp/orders/{id}/cancel", (c, p, q, b, m, o) -> c.estamp().orders().cancel(p.get(0), o));
        INVOKERS.put("GET /v1/estamp/orders/{id}/export", (c, p, q, b, m, o) -> c.estamp().orders().export(p.get(0), o));
        INVOKERS.put("GET /v1/estamp/orders/{id}/stamps", (c, p, q, b, m, o) -> c.estamp().orders().listStamps(p.get(0), o));
        INVOKERS.put("POST /v1/estamp/orders/{orderId}/documents", (c, p, q, b, m, o) -> {
            JsonNode file = null;
            for (JsonNode part : m.get("parts")) {
                if (part.get("name").asText().equals("file")) {
                    file = part;
                }
            }
            return c.estamp().orders().uploadDocument(p.get(0), DocumentUpload.of(file.get("filename").asText(),
                    file.get("contentType").asText(), file.get("content").asText().getBytes(StandardCharsets.UTF_8)), o);
        });
        INVOKERS.put("GET /v1/estamp/stamps/{stampId}", (c, p, q, b, m, o) -> c.estamp().stamps().get(p.get(0), o));
        INVOKERS.put("POST /v1/estamp/stamps/{stampId}/cancel", (c, p, q, b, m, o) -> c.estamp().stamps().cancel(p.get(0), o));
        INVOKERS.put("POST /v1/estamp/stamps/{stampId}/replace", (c, p, q, b, m, o) -> c.estamp().stamps().replace(p.get(0), o));
        INVOKERS.put("POST /v1/estamp/stamps/{stampId}/pan-card", (c, p, q, b, m, o) -> c.estamp().stamps().setPanCard(p.get(0), b, o));
        INVOKERS.put("GET /v1/estamp/stamps/{stampId}/certificate", (c, p, q, b, m, o) -> c.estamp().stamps().getCertificate(p.get(0), o));
        INVOKERS.put("GET /v1/stamp-duty-wallet", (c, p, q, b, m, o) -> c.wallets().stampDuty().getBalance(o));
        INVOKERS.put("POST /v1/stamp-duty-wallet/topup", (c, p, q, b, m, o) -> c.wallets().stampDuty().topup(b, o));
        INVOKERS.put("GET /v1/stamp-duty-wallet/transactions", (c, p, q, b, m, o) -> c.wallets().stampDuty()
                .listTransactions(intOf(q, "limit"), q.get("cursor"), o));
        INVOKERS.put("GET /v1/stamp-duty-wallet/invoices", (c, p, q, b, m, o) -> c.wallets().stampDuty()
                .listInvoices(intOf(q, "limit"), q.get("cursor"), o));
        INVOKERS.put("GET /v1/stamp-duty-wallet/invoices/{id}", (c, p, q, b, m, o) -> c.wallets().stampDuty().getInvoice(p.get(0), o));
        INVOKERS.put("GET /v1/service-fee-wallet", (c, p, q, b, m, o) -> c.wallets().serviceFee().getBalance(o));
        INVOKERS.put("POST /v1/service-fee-wallet/topup", (c, p, q, b, m, o) -> c.wallets().serviceFee().topup(b, o));
        INVOKERS.put("GET /v1/service-fee-wallet/transactions", (c, p, q, b, m, o) -> c.wallets().serviceFee()
                .listTransactions(intOf(q, "limit"), q.get("cursor"), o));
        INVOKERS.put("GET /v1/service-fee-wallet/invoices", (c, p, q, b, m, o) -> c.wallets().serviceFee()
                .listInvoices(intOf(q, "limit"), q.get("cursor"), o));
        INVOKERS.put("GET /v1/service-fee-wallet/invoices/{id}", (c, p, q, b, m, o) -> c.wallets().serviceFee().getInvoice(p.get(0), o));
        INVOKERS.put("GET /v1/account/balance", (c, p, q, b, m, o) -> c.account().getBalance(o));
        INVOKERS.put("GET /v1/account/usage", (c, p, q, b, m, o) -> c.account().getUsage(o));
    }

    private MockServer mock;

    @BeforeEach
    void setUp(MockServer server) {
        this.mock = server;
        mock.reset();
    }

    static Stream<SpecFixtures.Fixture> fixtures() throws IOException {
        List<SpecFixtures.Fixture> all = SpecFixtures.load(MockServerExtension.specDir()).stream()
                .filter(f -> INVOKERS.containsKey(f.method + " " + f.template)).collect(Collectors.toList());
        return all.stream();
    }

    private static List<String> pathParams(SpecFixtures.Fixture fx) {
        Matcher names = Pattern.compile("\\{(\\w+)}").matcher(fx.template);
        int count = 0;
        while (names.find()) {
            count++;
        }
        Matcher m = Pattern.compile("^" + fx.template.replaceAll("\\{\\w+}", "([^/]+)") + "$").matcher(fx.request.get("path").asText());
        assertThat(m.matches()).as(fx.file).isTrue();
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            out.add(m.group(i));
        }
        return out;
    }

    private static Map<String, String> query(SpecFixtures.Fixture fx) {
        Map<String, String> q = new LinkedHashMap<>();
        // POST fixtures list list-route query parameters that no POST route takes; only GET queries are replayed
        if (fx.method.equals("GET") && fx.request.has("query")) {
            fx.request.get("query").properties().forEach(e -> q.put(e.getKey(), e.getValue().asText()));
        }
        return q;
    }

    @SuppressWarnings("unchecked")
    private Model replay(SpecFixtures.Fixture fx, StampMitra client) {
        RequestOptions.Builder o = RequestOptions.builder().header("x-mock-scenario", fx.scenario);
        JsonNode sent = fx.request.get("headers");
        if (sent.has("Idempotency-Key")) {
            o.idempotencyKey(sent.get("Idempotency-Key").asText());
        }
        Map<String, Object> body = fx.request.has("body") && fx.method.equals("POST")
                ? Json.mapper().convertValue(fx.request.get("body"), Map.class) : null;
        return INVOKERS.get(fx.method + " " + fx.template).call(client, pathParams(fx), query(fx), body,
                fx.request.get("multipart"), o.build());
    }

    private static Class<? extends StampMitraException> errorType(int status) {
        switch (status) {
            case 400:
            case 422: return ValidationException.class;
            case 401: return AuthenticationException.class;
            case 402: return PaymentRequiredException.class;
            case 403: return PermissionException.class;
            case 404: return NotFoundException.class;
            case 409: return ConflictException.class;
            case 429: return RateLimitException.class;
            default: return UpstreamException.class;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void replayFixture(SpecFixtures.Fixture fx) {
        StampMitra client = StampMitra.builder().apiKey(KEY).baseUrl(mock.baseUrl()).maxRetries(0)
                .sleeper(d -> { }).build();
        if (fx.scenario.startsWith("error-")) {
            assertThatThrownBy(() -> replay(fx, client)).isExactlyInstanceOf(errorType(fx.status)).satisfies(e -> {
                StampMitraException ex = (StampMitraException) e;
                JsonNode err = fx.body.get("error");
                assertThat(ex.getStatus()).isEqualTo(fx.status);
                assertThat(ex.getErrorCode()).isEqualTo(err.get("code").asText());
                assertThat(ex.getMessage()).isEqualTo(err.get("message").asText());
                assertThat(ex.getRequestId()).isEqualTo(err.get("requestId").asText());
                JsonNode details = err.path("details");
                if (details.has("wallet")) {
                    assertThat(ex.getWallet()).hasToString(details.get("wallet").asText());
                } else {
                    assertThat(ex.getWallet()).isNull();
                }
                List<FieldIssue> expected = new ArrayList<>();
                for (JsonNode i : details.path("errors")) {
                    expected.add(new FieldIssue(i.path("field").asText(null), i.get("code").asText(), i.get("message").asText(),
                            i.has("index") ? Integer.valueOf(i.get("index").asInt()) : null));
                }
                assertThat(ex.getFieldErrors()).isEqualTo(expected);
                if (fx.status == 429 || (fx.status == 409 && fx.file.startsWith("post-v1-estamp-orders."))) {
                    assertThat(ex.getHeaders().keySet().stream().map(String::toLowerCase)).contains("retry-after");
                }
            });
        } else {
            Model result = replay(fx, client);
            assertThat(result.getResponse().getStatus()).isEqualTo(fx.status);
            JsonNode expectedBody = fx.body.isArray() ? Json.mapper().createObjectNode().set("items", fx.body) : fx.body;
            assertThat(Json.mapper().valueToTree(result.getRaw()).equals(Comparator.comparingDouble(JsonNode::asDouble), expectedBody))
                    .as("raw body equals %s", fx.file).isTrue();
        }
        assertRecordedRequest(fx);
    }

    private void assertRecordedRequest(SpecFixtures.Fixture fx) {
        JsonNode seen = mock.requests();
        assertThat(seen).as("one request for %s", fx.file).hasSize(1);
        JsonNode r = seen.get(0);
        assertThat(r.get("method").asText()).isEqualTo(fx.method);
        assertThat(r.get("path").asText()).isEqualTo(fx.request.get("path").asText());
        Map<String, String> recorded = new LinkedHashMap<>();
        r.get("query").properties().forEach(e -> recorded.put(e.getKey(), e.getValue().asText()));
        assertThat(recorded).isEqualTo(query(fx));
        assertThat(r.get("headers").get("authorization").asText()).isEqualTo("Bearer " + KEY);
        JsonNode sent = fx.request.get("headers");
        if (sent.has("Idempotency-Key")) {
            assertThat(r.get("headers").get("idempotency-key").asText()).isEqualTo(sent.get("Idempotency-Key").asText());
        } else {
            assertThat(r.get("headers").has("idempotency-key")).isFalse();
        }
        if (fx.request.has("multipart")) {
            assertThat(r.get("headers").get("content-type").asText()).startsWith("multipart/form-data; boundary=");
            List<JsonNode> expected = new ArrayList<>();
            fx.request.get("multipart").get("parts").forEach(expected::add);
            JsonNode parts = r.get("body").get("multipart");
            assertThat(parts).hasSize(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                JsonNode e = expected.get(i);
                JsonNode a = parts.get(i);
                assertThat(a.get("name").asText()).isEqualTo(e.get("name").asText());
                if (e.has("filename")) {
                    assertThat(a.get("filename").asText()).isEqualTo(e.get("filename").asText());
                    assertThat(a.get("contentType").asText()).isEqualTo(e.get("contentType").asText());
                    assertThat(a.get("size").asInt()).isEqualTo(e.get("content").asText().getBytes(StandardCharsets.UTF_8).length);
                } else {
                    assertThat(a.get("value").asText()).isEqualTo(e.get("value").asText());
                }
            }
        } else if (fx.method.equals("POST") && fx.request.has("body")) {
            assertThat(r.get("body").equals(Comparator.comparingDouble(JsonNode::asDouble), fx.request.get("body")))
                    .as("recorded body equals the fixture request body of %s", fx.file).isTrue();
        }
    }

    @org.junit.jupiter.api.Test
    void theTypedBuildersProduceTheDocumentedCreateBody() throws IOException {
        SpecFixtures.Fixture fx = SpecFixtures.success(SpecFixtures.load(mock.specDir()), "POST", "/v1/estamp/orders");
        in.stampmitra.sdk.requests.EstampOrderRequest built = in.stampmitra.sdk.requests.EstampOrderRequest.builder()
                .reference("order-2026-0917")
                .addStamp(in.stampmitra.sdk.requests.EstampStampLine.builder().clientReference("line-001").state("MH")
                        .category(in.stampmitra.sdk.requests.EstampStampLine.Category.E_STAMP).articleCode("ART1").denomination(1000)
                        .firstParty(in.stampmitra.sdk.requests.EstampParty.builder("Jane Doe").address("12 MG Road, Pune").build())
                        .secondParty(in.stampmitra.sdk.requests.EstampParty.builder("John Smith").build())
                        .purpose("Rental Agreement").build())
                .addStamp(in.stampmitra.sdk.requests.EstampStampLine.builder().clientReference("line-002").state("MH")
                        .category(in.stampmitra.sdk.requests.EstampStampLine.Category.E_STAMP).articleCode("ART1").denomination(500)
                        .firstParty(in.stampmitra.sdk.requests.EstampParty.builder("Jane Doe").build())
                        .secondParty(in.stampmitra.sdk.requests.EstampParty.builder("Acme Pvt Ltd").build())
                        .purpose("Indemnity Bond").build())
                .build();
        JsonNode actual = Json.mapper().valueToTree(built.toMap());
        assertThat(actual.equals(Comparator.comparingDouble(JsonNode::asDouble), fx.request.get("body"))).isTrue();
        in.stampmitra.sdk.requests.EstampOrderRequest.validate(built.toMap());
    }

    @org.junit.jupiter.api.Test
    void everyNewOperationHasFixturesAndAnInvoker() throws IOException {
        List<String> covered = fixtures().map(f -> f.method + " " + f.template).distinct().collect(Collectors.toList());
        assertThat(covered).containsExactlyInAnyOrderElementsOf(INVOKERS.keySet());
        assertThat(INVOKERS).hasSize(31);
        assertThat(fixtures().count()).isGreaterThan(190);
    }
}
