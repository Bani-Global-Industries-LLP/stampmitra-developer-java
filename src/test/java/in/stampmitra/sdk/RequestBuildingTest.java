package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.internal.Json;
import in.stampmitra.sdk.http.TransportRequest;
import in.stampmitra.sdk.requests.CreateOrderRequest;
import in.stampmitra.sdk.requests.CreatePaymentIntentRequest;
import in.stampmitra.sdk.requests.EstampDutyCalculateRequest;
import in.stampmitra.sdk.requests.EstampPartiesRequest;
import in.stampmitra.sdk.resources.LogsParams;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestBuildingTest {

    private final FakeTransport t = new FakeTransport().thenOk("{}");
    private final StampMitra client = TestClients.client(t);

    private JsonNode body() throws IOException {
        return Json.mapper().readTree(new String(t.last().getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void verifyBodiesAreNormalisedAndCompact() throws IOException {
        client.verify().pan(" abcde1234f ");
        assertThat(t.last().getUri().getPath()).isEqualTo("/v1/verify/pan");
        assertThat(body().toString()).isEqualTo("{\"pan\":\"ABCDE1234F\"}");
        client.verify().pan("ABCDE1234F", "Rahul Kumar");
        assertThat(body().toString()).isEqualTo("{\"pan\":\"ABCDE1234F\",\"name\":\"Rahul Kumar\"}");
        client.verify().gstin("27aabct1332l1zz", "ACME");
        assertThat(body().toString()).isEqualTo("{\"gstin\":\"27AABCT1332L1ZZ\",\"businessName\":\"ACME\"}");
        client.verify().drivingLicense("mh1420110012345", "1990-01-31");
        assertThat(body().toString()).isEqualTo("{\"dlNumber\":\"MH1420110012345\",\"dob\":\"1990-01-31\"}");
        client.verify().passport("PA1079341954215", "1990-01-31", "Rahul");
        assertThat(body().toString()).isEqualTo("{\"fileNumber\":\"PA1079341954215\",\"dob\":\"1990-01-31\",\"name\":\"Rahul\"}");
        client.verify().bankAccount("000123456789", "icic0001234", null, "9876543210");
        assertThat(body().toString())
                .isEqualTo("{\"accountNumber\":\"000123456789\",\"ifsc\":\"ICIC0001234\",\"phone\":\"9876543210\"}");
        client.verify().udyam("udyam-mh-12-1234567");
        assertThat(body().toString()).isEqualTo("{\"udyamNumber\":\"UDYAM-MH-12-1234567\"}");
        client.verify().voterId("abc1234567");
        assertThat(body().toString()).isEqualTo("{\"voterId\":\"ABC1234567\"}");
        client.verify().rc("mh12ab1234");
        assertThat(body().toString()).isEqualTo("{\"vehicleNumber\":\"MH12AB1234\"}");
        client.verify().cin("u74999dl2015ptc284009");
        assertThat(body().toString()).isEqualTo("{\"cin\":\"U74999DL2015PTC284009\"}");
        client.verify().panToGstin("ABCDE1234F");
        client.verify().panToUdyam("ABCDE1234F");
        assertThat(t.requests()).hasSize(12);
    }

    @Test
    void separatorsInVehiclePassportAndLicenceNumbersAreNotStripped() {
        assertThatThrownBy(() -> client.verify().rc("MH-12 AB 1234")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.verify().passport("PA-1079341954215", "1990-01-31"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.verify().drivingLicense("MH14 20110012345", "1990-01-31"))
                .isInstanceOf(ValidationException.class);
        assertThat(t.requests()).isEmpty();
    }

    @Test
    void invalidInputFailsLocallyWithTheFieldName() {
        assertThatThrownBy(() -> client.verify().pan("bogus")).isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    assertThat(((ValidationException) e).getField()).isEqualTo("pan");
                    assertThat(((ValidationException) e).getStatus()).isNull();
                    assertThat(e.getMessage()).doesNotContain("bogus");
                });
        assertThatThrownBy(() -> client.verify().pan(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.verify().pan("ABCDE1234F", "")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.verify().drivingLicense("MH1420110012345", "1990-13-45"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.verify().bankAccount("123456789", "ICIC0001234", null, "12345"))
                .isInstanceOf(ValidationException.class);
        assertThat(t.requests()).isEmpty();
    }

    @Test
    void validationCanBeSwitchedOff() throws IOException {
        StampMitra lax = TestClients.builder(t, TestClients.sleeps()).validateInput(false).build();
        lax.verify().pan("not-a-pan");
        assertThat(body().get("pan").asText()).isEqualTo("not-a-pan");
        assertThatThrownBy(() -> lax.verify().pan(null)).isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", "a/b", "a\\b", "a\nb", "../x"})
    void unsafePathIdsAreRejected(String id) {
        assertThatThrownBy(() -> client.orders().get(id)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.orders().getDocumentDownload("o1", id)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.services().get(id)).isInstanceOf(ValidationException.class);
        assertThat(t.requests()).isEmpty();
    }

    @Test
    void pathIdsArePercentEncoded() {
        client.orders().get("ord 1?x=y#z%");
        assertThat(t.last().getUri().getRawPath()).isEqualTo("/v1/orders/ord%201%3Fx%3Dy%23z%25");
        client.services().get("estamp.instrument.rent_agreement");
        assertThat(t.last().getUri().getRawPath()).isEqualTo("/v1/services/estamp.instrument.rent_agreement");
        client.orders().get("ord_é");
        assertThat(t.last().getUri().getRawPath()).isEqualTo("/v1/orders/ord_%C3%A9");
    }

    @Test
    void orderRequestsAreValidatedAndSent() throws IOException {
        assertThatThrownBy(() -> client.orders().create(CreateOrderRequest.builder().externalOrderId("o").build()))
                .isInstanceOf(ValidationException.class).satisfies(e ->
                        assertThat(((ValidationException) e).getField()).isEqualTo("externalCustomerId"));
        assertThatThrownBy(() -> client.orders().create(CreateOrderRequest.builder()
                .externalOrderId(new String(new char[129]).replace('\0', 'a')).externalCustomerId("c")
                .serviceCode("s").build())).isInstanceOf(ValidationException.class);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rent", 25000);
        client.orders().create(CreateOrderRequest.builder().externalOrderId("o1").externalCustomerId("c1")
                .serviceCode("estamp.instrument.rent_agreement").details(details).build());
        assertThat(t.last().getUri().getPath()).isEqualTo("/v1/orders");
        assertThat(body().get("details").get("rent").asInt()).isEqualTo(25000);
        Map<String, Object> viaMap = new LinkedHashMap<>();
        viaMap.put("externalOrderId", "o2");
        viaMap.put("externalCustomerId", "c2");
        viaMap.put("serviceCode", "s");
        viaMap.put("futureField", true);
        client.orders().create(viaMap);
        assertThat(body().get("futureField").asBoolean()).isTrue();
        assertThat(client.orders().create(viaMap).getRaw()).isEmpty();
    }

    @Test
    void paymentIntentAndDutyAndPartiesRequests() throws IOException {
        client.payments().intents().create(CreatePaymentIntentRequest.builder().externalOrderId("o1")
                .amountMinor(50000L).currency("INR").paymentConnectionId("pc_1").build());
        assertThat(t.last().getUri().getPath()).isEqualTo("/v1/payments/intents");
        assertThat(body().get("amountMinor").asLong()).isEqualTo(50000L);
        assertThatThrownBy(() -> client.payments().intents().create(CreatePaymentIntentRequest.builder()
                .externalOrderId("o1").amountMinor(1).currency("IN").build())).isInstanceOf(ValidationException.class);
        client.estamp().calculateDuty(EstampDutyCalculateRequest.builder().stateCode("MH").articleCode("5")
                .stampDutyAmount(new BigDecimal("100.50")).doorstepDelivery(true).build());
        assertThat(body().get("stampDutyAmount").decimalValue()).isEqualByComparingTo("100.50");
        assertThatThrownBy(() -> client.estamp().calculateDuty(EstampDutyCalculateRequest.builder().stateCode("MH")
                .build())).isInstanceOf(ValidationException.class).satisfies(e ->
                        assertThat(((ValidationException) e).getField()).isEqualTo("articleCode"));
        client.estamp().addParties("o1", EstampPartiesRequest.builder().customerName("A").partyName("B").build());
        assertThat(t.last().getUri().getPath()).isEqualTo("/v1/estamp/orders/o1/parties");
        assertThat(body().get("partyName").asText()).isEqualTo("B");
    }

    @Test
    void queryParametersAreEncodedAndNullsSkipped() {
        client.logs().list(LogsParams.builder().service("pan.verify").status("success").limit(25)
                .from(Instant.parse("2026-10-01T00:00:00Z")).cursor("a b+c/=").build());
        assertThat(t.last().getUri().getRawQuery()).isEqualTo(
                "from=2026-10-01T00%3A00%3A00Z&service=pan.verify&status=success&limit=25&cursor=a%20b%2Bc%2F%3D");
        client.logs().list();
        assertThat(t.last().getUri().getRawQuery()).isNull();
        client.webhooks().events().list(20, null);
        assertThat(t.last().getUri().getRawQuery()).isEqualTo("limit=20");
    }

    @Test
    void logParamsAreValidated() {
        assertThatThrownBy(() -> client.logs().list(LogsParams.builder().status("maybe").build()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.logs().list(LogsParams.builder().limit(0).build()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> client.logs().list(LogsParams.builder().limit(201).build()))
                .isInstanceOf(ValidationException.class);
        assertThat(t.requests()).isEmpty();
    }

    @Test
    void perRequestOptionsApply() {
        client.usage().summary(RequestOptions.builder().header("X-Request-Id", "my-id").timeout(Duration.ofSeconds(3))
                .build());
        TransportRequest r = t.last();
        assertThat(r.getHeaders()).containsEntry("X-Request-Id", "my-id");
        assertThat(r.getTimeout()).isEqualTo(Duration.ofSeconds(3));
        client.usage().summary();
        assertThat(t.last().getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThatThrownBy(() -> client.usage().summary(RequestOptions.builder().timeout(Duration.ZERO).build()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rawResponseMetadataIsAvailable() {
        FakeTransport meta = new FakeTransport().then(201, "{\"referenceId\":\"SMV-1\",\"valid\":true}", "X-Request-Id",
                "req-42", "X-Other", "1");
        in.stampmitra.sdk.models.PanVerification pan = TestClients.client(meta).verify().pan("ABCDE1234F");
        assertThat(pan.getResponse().getStatus()).isEqualTo(201);
        assertThat(pan.getResponse().getRequestId()).isEqualTo("req-42");
        assertThat(pan.getResponse().getHeader("x-other")).isEqualTo("1");
        assertThat(pan.getResponse().getAttempts()).isEqualTo(1);
        assertThat(pan.isValid()).isTrue();
    }
}
