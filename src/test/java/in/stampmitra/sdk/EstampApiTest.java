package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.errors.ConflictException;
import in.stampmitra.sdk.errors.FieldIssue;
import in.stampmitra.sdk.errors.PaymentRequiredException;
import in.stampmitra.sdk.errors.StampMitraException;
import in.stampmitra.sdk.errors.UpstreamException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.errors.WalletType;
import in.stampmitra.sdk.http.TransportRequest;
import in.stampmitra.sdk.internal.Json;
import in.stampmitra.sdk.models.StampDutyWalletTopup;
import in.stampmitra.sdk.models.ServiceFeeWalletTopup;
import in.stampmitra.sdk.requests.DocumentUpload;
import in.stampmitra.sdk.requests.EstampOrderRequest;
import in.stampmitra.sdk.requests.EstampPanCardRequest;
import in.stampmitra.sdk.requests.EstampParty;
import in.stampmitra.sdk.requests.EstampPropertyArea;
import in.stampmitra.sdk.requests.EstampStampLine;
import in.stampmitra.sdk.requests.WalletTopupRequest;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/** e-Stamp, wallet and account behaviour that needs no network: bodies, keys, retry rules, errors. */
class EstampApiTest {

    private static final String ERR = "{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"boom\",\"requestId\":\"r1\"}}";
    private static final java.util.regex.Pattern UUID_V4 =
            java.util.regex.Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private final List<Duration> sleeps = TestClients.sleeps();

    private StampMitra client(FakeTransport t) {
        return TestClients.builder(t, sleeps).build();
    }

    private static EstampStampLine line() {
        return EstampStampLine.builder().state("MH").articleCode("ART1").denomination(new BigDecimal("1000")).firstParty("Jane Doe").build();
    }

    private static EstampOrderRequest order() {
        return EstampOrderRequest.builder().reference("r-1").addStamp(line()).build();
    }

    // ---------------------------------------------------------------- idempotency keys

    @Test
    void createOrderGeneratesAKeyWhenTheCallerOmitsOneAndReusesItOnRetry() {
        FakeTransport t = new FakeTransport().then(503, ERR).then(502, ERR).then(201, "{\"id\":\"estord_1\",\"status\":\"PENDING_REVIEW\"}");
        assertThat(client(t).estamp().orders().create(order()).getId()).isEqualTo("estord_1");
        assertThat(t.requests()).hasSize(3);
        Set<String> keys = new HashSet<>();
        t.requests().forEach(r -> keys.add(r.getHeaders().get("Idempotency-Key")));
        assertThat(keys).hasSize(1).allSatisfy(k -> assertThat(k).matches(UUID_V4));
    }

    @Test
    void createOrderUsesTheCallerKey() {
        FakeTransport t = new FakeTransport().then(201, "{\"id\":\"e\"}");
        client(t).estamp().orders().create(order(), RequestOptions.withIdempotencyKey("lease-2026-0412-v1"));
        assertThat(t.last().getHeaders().get("Idempotency-Key")).isEqualTo("lease-2026-0412-v1");
    }

    @Test
    void validateTakesNoKeyAndIsRetriedBecauseItIsARetrySafeDryRun() {
        FakeTransport t = new FakeTransport().then(503, ERR).then(200, "{\"valid\":true}");
        assertThat(client(t).estamp().orders().validate(order()).get("valid")).isEqualTo(true);
        assertThat(t.requests()).hasSize(2);
        t.requests().forEach(r -> assertThat(r.getHeaders()).doesNotContainKey("Idempotency-Key"));
    }

    // ---------------------------------------------------------------- never-retry set

    private void neverRetried(String label, BiConsumer<StampMitra, RequestOptions> call) {
        for (RequestOptions o : new RequestOptions[] {null, RequestOptions.withIdempotencyKey("caller-key")}) {
            FakeTransport t = new FakeTransport().then(503, ERR, "Retry-After", "1").then(200, "{}");
            sleeps.clear();
            assertThatThrownBy(() -> call.accept(client(t), o)).as(label).isInstanceOf(UpstreamException.class);
            assertThat(t.requests()).as(label + " attempts").hasSize(1);
            assertThat(sleeps).isEmpty();
        }
        FakeTransport timeout = new FakeTransport().thenThrow(new java.net.http.HttpTimeoutException("slow")).then(200, "{}");
        assertThatThrownBy(() -> call.accept(client(timeout), RequestOptions.withIdempotencyKey("caller-key")))
                .as(label + " timeout").isInstanceOf(StampMitraException.class);
        assertThat(timeout.requests()).hasSize(1);
        FakeTransport limited = new FakeTransport().then(429, ERR, "Retry-After", "1").then(200, "{}");
        assertThatThrownBy(() -> call.accept(client(limited), RequestOptions.withIdempotencyKey("caller-key")))
                .as(label + " 429").isInstanceOf(in.stampmitra.sdk.errors.RateLimitException.class);
        assertThat(limited.requests()).hasSize(1);
    }

    @Test
    void cancelReplacePanCardTopupsAndReplayAreNeverRetriedEvenWithACallerKey() {
        WalletTopupRequest topup = WalletTopupRequest.builder().baseAmount(1000).build();
        EstampPanCardRequest pan = EstampPanCardRequest.builder().panCardImage("https://cdn.example.com/p.jpg").build();
        neverRetried("order cancel", (c, o) -> c.estamp().orders().cancel("estord_1", o));
        neverRetried("stamp cancel", (c, o) -> c.estamp().stamps().cancel("eststamp_1", o));
        neverRetried("stamp replace", (c, o) -> c.estamp().stamps().replace("eststamp_1", o));
        neverRetried("pan-card link", (c, o) -> c.estamp().stamps().setPanCard("eststamp_1", pan, o));
        neverRetried("stamp duty topup", (c, o) -> c.wallets().stampDuty().topup(topup, o));
        neverRetried("service fee topup", (c, o) -> c.wallets().serviceFee().topup(topup, o));
        neverRetried("webhook replay", (c, o) -> c.webhooks().events().replay("evt_1", o));
    }

    @Test
    void cancelSendsNoKeyByDefault() {
        FakeTransport t = new FakeTransport().then(200, "{\"id\":\"estord_1\",\"status\":\"CANCELLED\"}");
        client(t).estamp().orders().cancel("estord_1");
        client(t).wallets().serviceFee().topup(WalletTopupRequest.builder().baseAmount(10).build());
        t.requests().forEach(r -> assertThat(r.getHeaders()).doesNotContainKey("Idempotency-Key"));
    }

    // ---------------------------------------------------------------- Retry-After

    @Test
    void retryAfterIsHonouredOn429And503ForGet() {
        FakeTransport t = new FakeTransport().then(429, ERR, "Retry-After", "3").then(503, ERR, "Retry-After", "2").then(200, "{\"balance\":\"1.00\",\"currency\":\"INR\"}");
        assertThat(client(t).wallets().stampDuty().getBalance().getBalance()).isEqualTo("1.00");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(2));
    }

    @Test
    void conflictInProgressIsRetriedAfterRetryAfterWithTheSameKey() {
        String inProgress = "{\"error\":{\"code\":\"IDEMPOTENCY_IN_PROGRESS\",\"message\":\"busy\",\"requestId\":\"r\"}}";
        FakeTransport t = new FakeTransport().then(409, inProgress, "Retry-After", "4").then(201, "{\"id\":\"estord_1\"}");
        client(t).estamp().orders().create(order());
        assertThat(sleeps).containsExactly(Duration.ofSeconds(4));
        assertThat(t.requests()).hasSize(2);
        assertThat(t.requests().get(0).getHeaders().get("Idempotency-Key")).isEqualTo(t.requests().get(1).getHeaders().get("Idempotency-Key"));
    }

    @Test
    void otherConflictsAreNotRetried() {
        FakeTransport t = new FakeTransport().then(409, "{\"error\":{\"code\":\"CONFLICT\",\"message\":\"not cancellable\",\"requestId\":\"r\"}}", "Retry-After", "1").then(200, "{}");
        assertThatThrownBy(() -> client(t).estamp().orders().create(order())).isInstanceOf(ConflictException.class);
        assertThat(t.requests()).hasSize(1);
    }

    // ---------------------------------------------------------------- errors

    @Test
    void validationDetailsAndStatus422AreExposed() {
        String body = "{\"error\":{\"code\":\"BAD_REQUEST\",\"message\":\"VALIDATION_FAILED: stamps[0].articleCode: required\","
                + "\"details\":{\"errors\":[{\"field\":\"stamps[0].articleCode\",\"code\":\"REQUIRED\",\"message\":\"articleCode is required\",\"index\":0},"
                + "{\"field\":\"stamps[1].state\",\"code\":\"MIXED_STATE\",\"message\":\"m\"}]},\"requestId\":\"r\"}}";
        FakeTransport t = new FakeTransport().then(400, body);
        assertThatThrownBy(() -> client(t).estamp().orders().validate(order())).isExactlyInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    StampMitraException ex = (StampMitraException) e;
                    assertThat(ex.getErrorCode()).isEqualTo("BAD_REQUEST");
                    assertThat(ex.getFieldErrors()).containsExactly(
                            new FieldIssue("stamps[0].articleCode", "REQUIRED", "articleCode is required", 0),
                            new FieldIssue("stamps[1].state", "MIXED_STATE", "m", null));
                    assertThat(ex.getFieldErrors().get(0).getIndex()).isEqualTo(0);
                    assertThat(ex.getDetails()).containsKey("errors");
                    assertThat(ex.getWallet()).isNull();
                });
        FakeTransport reused = new FakeTransport().then(422, "{\"error\":{\"code\":\"IDEMPOTENCY_KEY_REUSED\",\"message\":\"x\",\"requestId\":\"r\"}}");
        assertThatThrownBy(() -> client(reused).estamp().orders().create(order())).isExactlyInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    assertThat(((StampMitraException) e).getStatus()).isEqualTo(422);
                    assertThat(((StampMitraException) e).getErrorCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
        assertThat(reused.requests()).hasSize(1);
    }

    @Test
    void walletOnA402IsTyped() {
        for (WalletType w : WalletType.values()) {
            FakeTransport t = new FakeTransport().then(402, "{\"error\":{\"code\":\"PAYMENT_REQUIRED\",\"message\":\"short\",\"details\":{\"wallet\":\""
                    + w.name() + "\"},\"requestId\":\"r\"}}");
            assertThatThrownBy(() -> client(t).estamp().orders().create(order())).isExactlyInstanceOf(PaymentRequiredException.class)
                    .satisfies(e -> assertThat(((StampMitraException) e).getWallet()).isEqualTo(w));
        }
        FakeTransport unknown = new FakeTransport().then(402, "{\"error\":{\"code\":\"PAYMENT_REQUIRED\",\"message\":\"short\",\"details\":{\"wallet\":\"API_CREDIT\"},\"requestId\":\"r\"}}");
        assertThatThrownBy(() -> client(unknown).estamp().orders().create(order()))
                .satisfies(e -> assertThat(((StampMitraException) e).getWallet()).isNull());
    }

    @Test
    void errorsWithoutDetailsHaveEmptyGetters() {
        FakeTransport t = new FakeTransport().then(404, "<html>no</html>");
        assertThatThrownBy(() -> client(t).estamp().orders().get("x")).satisfies(e -> {
            StampMitraException ex = (StampMitraException) e;
            assertThat(ex.getFieldErrors()).isEmpty();
            assertThat(ex.getDetails()).isEmpty();
            assertThat(ex.getWallet()).isNull();
        });
    }

    // ---------------------------------------------------------------- multipart

    private static String text(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    @Test
    void uploadSendsAMultipartBodyWithFileAndTypeParts() {
        FakeTransport t = new FakeTransport().then(201, "{\"documentId\":\"estdoc_1\",\"status\":\"RECEIVED\"}");
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        assertThat(client(t).estamp().orders().uploadDocument("estord_1", DocumentUpload.of("pan card.pdf", pdf)).getDocumentId())
                .isEqualTo("estdoc_1");
        TransportRequest r = t.last();
        assertThat(r.getUri().getPath()).isEqualTo("/v1/estamp/orders/estord_1/documents");
        String contentType = r.getHeaders().get("Content-Type");
        assertThat(contentType).startsWith("multipart/form-data; boundary=");
        assertThat(r.getHeaders().get("Idempotency-Key")).matches(UUID_V4);
        String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
        String body = text(r.getBody());
        assertThat(body).startsWith("--" + boundary + "\r\n").endsWith("--" + boundary + "--\r\n");
        assertThat(body).contains("Content-Disposition: form-data; name=\"file\"; filename=\"pan card.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.4\n%%EOF\n\r\n");
        assertThat(body).contains("Content-Disposition: form-data; name=\"type\"\r\n\r\npan_card\r\n");
    }

    @Test
    void aRetriedUploadResendsTheIdenticalBodyAndKey() {
        FakeTransport t = new FakeTransport().then(502, ERR).then(201, "{\"documentId\":\"d\"}");
        client(t).estamp().orders().uploadDocument("estord_1", DocumentUpload.of("p.png", new byte[] {1, 2, 3}));
        assertThat(t.requests()).hasSize(2);
        assertThat(t.requests().get(0).getBody()).isEqualTo(t.requests().get(1).getBody());
        assertThat(t.requests().get(0).getHeaders().get("Idempotency-Key")).isEqualTo(t.requests().get(1).getHeaders().get("Idempotency-Key"));
        assertThat(t.requests().get(1).getHeaders().get("Content-Type")).startsWith("multipart/form-data");
    }

    @Test
    void uploadFilenamesCannotInjectHeadersAndNonAsciiGetsAnExtendedParameter() {
        FakeTransport t = new FakeTransport().then(201, "{}");
        client(t).estamp().orders().uploadDocument("o", DocumentUpload.of("a\"\r\nX: yé.pdf", new byte[] {1}));
        String body = text(t.last().getBody());
        assertThat(body).doesNotContain("\r\nX: y").contains("filename*=UTF-8''");
    }

    @Test
    void uploadLimitsAreCheckedBeforeSending() {
        FakeTransport t = new FakeTransport().then(201, "{}");
        StampMitra c = client(t);
        assertThatThrownBy(() -> c.estamp().orders().uploadDocument("o", DocumentUpload.of("p.pdf", new byte[0])))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> c.estamp().orders().uploadDocument("o", DocumentUpload.of("p.pdf", new byte[DocumentUpload.MAX_BYTES + 1])))
                .isInstanceOf(ValidationException.class);
        assertThat(t.requests()).isEmpty();
        c.estamp().orders().uploadDocument("o", DocumentUpload.of("p.pdf", new byte[DocumentUpload.MAX_BYTES]));
        assertThat(t.requests()).hasSize(1);
    }

    @Test
    void documentUploadFromAPathReadsTheFile() throws IOException {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("pan", ".jpeg");
        try {
            java.nio.file.Files.write(f, new byte[] {9, 9});
            DocumentUpload u = DocumentUpload.of(f);
            assertThat(u.getContentType()).isEqualTo("image/jpeg");
            assertThat(u.getContent()).containsExactly(9, 9);
            assertThat(u.getFilename()).endsWith(".jpeg");
        } finally {
            java.nio.file.Files.delete(f);
        }
        assertThatThrownBy(() -> DocumentUpload.of(java.nio.file.Paths.get("/nonexistent/pan.pdf"))).isInstanceOf(ValidationException.class);
    }

    // ---------------------------------------------------------------- request types

    @Test
    void partiesSerialiseAsAStringOrAnObject() throws Exception {
        EstampStampLine l = EstampStampLine.builder().state("MH").articleCode("ART1").denomination(new BigDecimal("500"))
                .firstParty(EstampParty.builder("Jane Doe").address("12 MG Road, Pune").phone("9876543210")
                        .fatherHusbandName("R Doe").gender(EstampParty.Gender.FEMALE).build())
                .secondParty("John Smith").stampDutyPayer("FIRST_PARTY").payerGender(EstampParty.Gender.OTHER)
                .propertyArea(EstampPropertyArea.of(EstampPropertyArea.Unit.SQUARE_FEET, 850))
                .category(EstampStampLine.Category.TRADITIONAL_STAMP).quantity(3).panCardImage("https://cdn.example.com/p.jpg").build();
        assertThat(Json.toJson(l.toMap())).isEqualTo("{\"state\":\"MH\",\"articleCode\":\"ART1\",\"denomination\":500,"
                + "\"firstParty\":{\"name\":\"Jane Doe\",\"address\":\"12 MG Road, Pune\",\"phone\":\"9876543210\",\"fatherHusbandName\":\"R Doe\",\"gender\":\"FEMALE\"},"
                + "\"secondParty\":\"John Smith\",\"stampDutyPayer\":\"FIRST_PARTY\",\"payerGender\":\"OTHER\","
                + "\"propertyArea\":{\"unit\":\"Square Feet\",\"value\":850.0},\"category\":\"traditional_stamp\",\"quantity\":3,"
                + "\"panCardImage\":\"https://cdn.example.com/p.jpg\"}");
        assertThat(EstampParty.of("Jane").toWire()).isEqualTo("Jane");
        assertThat(EstampParty.builder("Jane").build().toWire()).isEqualTo(java.util.Collections.singletonMap("name", "Jane"));
        assertThat(Json.toJson(EstampPropertyArea.notApplicable().toWire())).isEqualTo("{\"unit\":\"Not Applicable\"}");
        assertThat(EstampPropertyArea.Unit.SQUARE_YARD.wireName()).isEqualTo("Square Yard");
        assertThat(EstampPropertyArea.Unit.SQUARE_METERS.wireName()).isEqualTo("Square Meters");
        assertThatThrownBy(() -> EstampParty.of(" ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> EstampParty.of(new String(new char[151]).replace('\0', 'a'))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> EstampPropertyArea.of(EstampPropertyArea.Unit.SQUARE_FEET, BigDecimal.ZERO)).isInstanceOf(ValidationException.class);
    }

    private void rejects(Map<String, Object> body, String field) {
        FakeTransport t = new FakeTransport().then(200, "{}");
        assertThatThrownBy(() -> client(t).estamp().orders().validate(body)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo(field));
        assertThat(t.requests()).isEmpty();
    }

    private static EstampOrderRequest.Builder b() {
        return EstampOrderRequest.builder();
    }

    @Test
    void orderLimitsAreCheckedLocally() {
        rejects(b().build().toMap(), "stamps");
        EstampOrderRequest.Builder many = b();
        for (int i = 0; i < EstampOrderRequest.MAX_STAMPS + 1; i++) {
            many.addStamp(line());
        }
        rejects(many.build().toMap(), "stamps");
        rejects(b().addStamp(line()).addStamp(EstampStampLine.builder().state("KA").articleCode("A").denomination(1).firstParty("x").build())
                .build().toMap(), "stamps[1].state");
        rejects(b().addStamp(EstampStampLine.builder().state("MH").articleCode("A").denomination(1).firstParty("x").quantity(1001).build())
                .build().toMap(), "stamps[0].quantity");
        rejects(b().addStamp(EstampStampLine.builder().state("MH").articleCode("A").denomination(1).firstParty("x").quantity(0).build())
                .build().toMap(), "stamps[0].quantity");
        rejects(b().addStamp(EstampStampLine.builder().state("MH").denomination(1).firstParty("x").build()).build().toMap(), "stamps[0].articleCode");
        rejects(b().addStamp(EstampStampLine.builder().state("MH").articleCode("A").denomination(0).firstParty("x").build()).build().toMap(), "stamps[0].denomination");
        rejects(b().addStamp(EstampStampLine.builder().state("MH").articleCode("A").denomination(1).build()).build().toMap(), "stamps[0].firstParty");
        rejects(b().addStamp(EstampStampLine.builder().state("MH").articleCode("A").denomination(1).firstParty("x")
                .panCardImage("http://insecure.example.com/p.jpg").build()).build().toMap(), "stamps[0].panCardImage");
    }

    @Test
    void anOrderOfExactlyTheMaximumSizeAndMixedCaseStateIsAccepted() {
        FakeTransport t = new FakeTransport().then(200, "{\"valid\":true}");
        EstampOrderRequest.Builder many = b();
        for (int i = 0; i < EstampOrderRequest.MAX_STAMPS; i++) {
            many.addStamp(i % 2 == 0 ? line() : EstampStampLine.builder().state("mh").articleCode("A").denomination(1)
                    .firstParty("x").quantity(EstampOrderRequest.MAX_QUANTITY).build());
        }
        client(t).estamp().orders().validate(many.build());
        assertThat(t.requests()).hasSize(1);
    }

    @Test
    void createRequestBodyHasTheOrderShape() throws Exception {
        FakeTransport t = new FakeTransport().then(201, "{\"id\":\"e\"}");
        client(t).estamp().orders().create(order());
        assertThat(new String(t.last().getBody(), StandardCharsets.UTF_8)).isEqualTo(
                "{\"reference\":\"r-1\",\"stamps\":[{\"state\":\"MH\",\"articleCode\":\"ART1\",\"denomination\":1000,\"firstParty\":\"Jane Doe\"}]}");
        assertThat(t.last().getHeaders().get("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void panCardAndTopupBodiesAreValidated() {
        FakeTransport t = new FakeTransport().then(202, "{}");
        StampMitra c = client(t);
        assertThatThrownBy(() -> c.estamp().stamps().setPanCard("s", EstampPanCardRequest.builder().panCardImage("http://x.example.com/a.jpg").build()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> c.estamp().stamps().setPanCard("s", EstampPanCardRequest.builder().build())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> c.wallets().stampDuty().topup(WalletTopupRequest.builder().baseAmount(0).build())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> c.wallets().serviceFee().topup(WalletTopupRequest.builder().baseAmount(-5).build())).isInstanceOf(ValidationException.class);
        assertThat(t.requests()).isEmpty();
        c.estamp().stamps().setPanCard("s", EstampPanCardRequest.builder().panCardImage("https://cdn.example.com/a.jpg").build());
        assertThat(new String(t.last().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"panCardImage\":\"https://cdn.example.com/a.jpg\"}");
    }

    @Test
    void queryParametersAreEncodedAndOmittedWhenNull() {
        FakeTransport t = new FakeTransport().then(200, "{}");
        StampMitra c = client(t);
        c.estamp().getStateRequirements("MH", "ART1", new BigDecimal("1E+3"), "e_stamp");
        assertThat(t.last().getUri().getRawQuery()).isEqualTo("articleCode=ART1&denomination=1000&category=e_stamp");
        c.estamp().getStateRequirements("MH");
        assertThat(t.last().getUri().getRawQuery()).isNull();
        c.estamp().orders().list(null, 50, "abc def");
        assertThat(t.last().getUri().getRawQuery()).isEqualTo("limit=50&cursor=abc%20def");
        c.estamp().getStatesAvailability(true);
        assertThat(t.last().getUri().getRawQuery()).isEqualTo("active=true");
    }

    // ---------------------------------------------------------------- wallet typing

    @Test
    void stampDutyTopupNumbersAndStringsKeepTheirJsonTypes() {
        String body = "{\"baseAmount\":1000,\"processingFeePercent\":\"6.00\",\"processingFeeAmount\":\"60.00\",\"gstAmount\":10.8,"
                + "\"totalCharged\":1070.8,\"creditAmount\":\"1000.00\",\"topupId\":\"inv_1\"}";
        FakeTransport t = new FakeTransport().then(201, body);
        StampDutyWalletTopup r = client(t).wallets().stampDuty().topup(WalletTopupRequest.builder().baseAmount(1000).build());
        assertThat(r.getBaseAmount()).isEqualByComparingTo("1000");
        assertThat(r.getGstAmount()).isEqualByComparingTo("10.8");
        assertThat(r.getTotalCharged()).isEqualByComparingTo("1070.8");
        assertThat(r.getProcessingFeePercent()).isEqualTo("6.00");
        assertThat(r.getProcessingFeeAmount()).isEqualTo("60.00");
        assertThat(r.getCreditAmount()).isEqualTo("1000.00");
        assertThat(r.getTopupId()).isEqualTo("inv_1");
        FakeTransport sandbox = new FakeTransport().then(200, "{\"baseAmount\":1,\"processingFeePercent\":\"6.00\",\"processingFeeAmount\":\"0.06\",\"gstAmount\":0.01,\"totalCharged\":1.07,\"creditAmount\":\"1.00\",\"sandbox\":true}");
        assertThat(client(sandbox).wallets().stampDuty().topup(WalletTopupRequest.builder().baseAmount(BigDecimal.ONE).build()).getTopupId()).isNull();
        assertThat(new String(sandbox.last().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"baseAmount\":1}");
    }

    @Test
    void serviceFeeTopupAmountsAreStrings() {
        FakeTransport t = new FakeTransport().then(200, "{\"baseAmount\":\"1000.00\",\"gstAmount\":\"180.00\",\"processingFeePercent\":\"0.00\","
                + "\"processingFeeAmount\":\"0.00\",\"creditAmount\":\"1000.00\",\"totalCharged\":\"1180.00\"}");
        ServiceFeeWalletTopup r = client(t).wallets().serviceFee().topup(WalletTopupRequest.builder().baseAmount(1000).build());
        assertThat(r.getBaseAmount()).isEqualTo("1000.00");
        assertThat(r.getGstAmount()).isEqualTo("180.00");
        assertThat(r.getTotalCharged()).isEqualTo("1180.00");
    }

    @Test
    void bareArrayResponsesAreIterable() {
        FakeTransport t = new FakeTransport().then(200, "[{\"code\":\"PUNE\",\"name\":\"Pune\"},{\"code\":\"MUM\",\"name\":\"Mumbai\"}]")
                .then(200, "[{\"value\":\"SRO-14\",\"label\":\"Haveli 14\"}]");
        List<String> codes = new ArrayList<>();
        client(t).estamp().listDistricts("MH").forEach(d -> codes.add(d.getCode()));
        assertThat(codes).containsExactly("PUNE", "MUM");
        assertThat(client(t).estamp().listSros("MH", "PUNE").getItems().get(0).getValue()).isEqualTo("SRO-14");
    }

    @Test
    void pathIdsAreEncoded() {
        FakeTransport t = new FakeTransport().then(200, "{}");
        client(t).estamp().stamps().get("a b");
        assertThat(t.last().getUri().getRawPath()).isEqualTo("/v1/estamp/stamps/a%20b");
        assertThatThrownBy(() -> client(t).estamp().orders().get("../x")).isInstanceOf(ValidationException.class);
    }
}
