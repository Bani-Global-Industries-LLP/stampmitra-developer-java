package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.errors.NotFoundException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.models.LogPage;
import in.stampmitra.sdk.models.OrderReference;
import in.stampmitra.sdk.models.PanVerification;
import in.stampmitra.sdk.requests.CreateOrderRequest;
import in.stampmitra.sdk.resources.LogsParams;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.ScriptedServer;
import in.stampmitra.sdk.support.ScriptedServer.Step;
import in.stampmitra.sdk.support.TestClients;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncTest {

    private static java.util.Map<String, Object> duty() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("stateCode", "MH");
        m.put("articleCode", "5");
        m.put("stampDutyAmount", 100);
        return m;
    }

    @Test
    void panAsyncCompletesWithTheModel() throws Exception {
        FakeTransport t = new FakeTransport().then(200, "{\"referenceId\":\"SMV-1\",\"valid\":true}");
        PanVerification pan = TestClients.client(t).verify().panAsync("ABCDE1234F", "Rahul", null).get(5, TimeUnit.SECONDS);
        assertThat(pan.getReferenceId()).isEqualTo("SMV-1");
        assertThat(t.requests()).hasSize(1);
    }

    @Test
    void everyVerifyOperationHasAnAsyncTwin() throws Exception {
        FakeTransport t = new FakeTransport().thenOk("{}");
        StampMitra c = TestClients.client(t);
        List<CompletableFuture<?>> fs = new ArrayList<>();
        fs.add(c.verify().panAsync("ABCDE1234F"));
        fs.add(c.verify().gstinAsync("27AABCT1332L1ZZ"));
        fs.add(c.verify().rcAsync("MH12AB1234"));
        fs.add(c.verify().voterIdAsync("ABC1234567"));
        fs.add(c.verify().drivingLicenseAsync("MH1420110012345", "1990-01-31"));
        fs.add(c.verify().passportAsync("PA1079341954215", "1990-01-31"));
        fs.add(c.verify().cinAsync("U74999DL2015PTC284009"));
        fs.add(c.verify().udyamAsync("UDYAM-MH-12-1234567"));
        fs.add(c.verify().panToGstinAsync("ABCDE1234F"));
        fs.add(c.verify().panToUdyamAsync("ABCDE1234F"));
        fs.add(c.verify().bankAccountAsync("123456789", "ICIC0001234"));
        fs.add(c.services().listAsync());
        fs.add(c.services().getAsync("s"));
        fs.add(c.estamp().getSchemaAsync("s"));
        fs.add(c.estamp().listStatesAsync());
        fs.add(c.estamp().listInstrumentsAsync("MH"));
        fs.add(c.estamp().listArticlesAsync("i"));
        fs.add(c.estamp().calculateDutyAsync(duty()));
        fs.add(c.estamp().addPartiesAsync("o", Collections.<String, Object>emptyMap()));
        fs.add(c.orders().getAsync("o"));
        fs.add(c.orders().getStatusAsync("o"));
        fs.add(c.orders().getPaymentAsync("o"));
        fs.add(c.orders().listDocumentsAsync("o"));
        fs.add(c.orders().getDocumentDownloadAsync("o", "d"));
        fs.add(c.payments().intents().getAsync("p"));
        fs.add(c.usage().summaryAsync());
        fs.add(c.logs().listAsync(LogsParams.none()));
        fs.add(c.webhooks().events().listAsync());
        fs.add(c.webhooks().events().replayAsync("e"));
        for (CompletableFuture<?> f : fs) {
            assertThat(f.get(5, TimeUnit.SECONDS)).isNotNull();
        }
        assertThat(t.requests()).hasSize(fs.size());
    }

    @Test
    void ordersCreateAsyncAndLogsAsync() throws Exception {
        FakeTransport t = new FakeTransport().then(201, "{\"externalOrderId\":\"o1\",\"createdAt\":\"2026-10-15T09:41:07.000Z\"}");
        OrderReference o = TestClients.client(t).orders().createAsync(CreateOrderRequest.builder().externalOrderId("o1")
                .externalCustomerId("c").serviceCode("s").build()).get(5, TimeUnit.SECONDS);
        assertThat(o.getExternalOrderId()).isEqualTo("o1");
        FakeTransport l = new FakeTransport().thenOk("{\"items\":[],\"nextCursor\":null}");
        LogPage page = TestClients.client(l).logs().listAsync(LogsParams.none()).get(5, TimeUnit.SECONDS);
        assertThat(page.getItems()).isEmpty();
    }

    @Test
    void validationErrorsCompleteTheFutureExceptionally() {
        FakeTransport t = new FakeTransport().thenOk("{}");
        CompletableFuture<PanVerification> f = TestClients.client(t).verify().panAsync("bogus");
        assertThatThrownBy(() -> f.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ValidationException.class);
        assertThatThrownBy(f::join).isInstanceOf(CompletionException.class).hasCauseInstanceOf(ValidationException.class);
        assertThat(t.requests()).isEmpty();
    }

    @Test
    void apiErrorsCompleteTheFutureExceptionally() {
        FakeTransport t = new FakeTransport().then(404, "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"gone\",\"requestId\":\"r\"}}");
        assertThatThrownBy(() -> TestClients.client(t).orders().getAsync("o").get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(NotFoundException.class);
    }

    @Test
    void aCustomExecutorIsUsed() throws Exception {
        AtomicInteger used = new AtomicInteger();
        Executor counting = r -> {
            used.incrementAndGet();
            r.run();
        };
        StampMitra c = TestClients.builder(new FakeTransport().thenOk("{}"), TestClients.sleeps()).executor(counting).build();
        c.usage().summaryAsync().get(5, TimeUnit.SECONDS);
        assertThat(used).hasValue(1);
    }

    @Test
    void manyCallsRunConcurrently() throws Exception {
        try (ScriptedServer s = new ScriptedServer(Step.slow(300, 200, "{\"referenceId\":\"r\",\"valid\":true}"))) {
            StampMitra c = StampMitra.builder().apiKey(TestClients.KEY).baseUrl(s.url()).build();
            long start = System.nanoTime();
            List<CompletableFuture<PanVerification>> fs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                fs.add(c.verify().panAsync("ABCDE1234F"));
            }
            for (CompletableFuture<PanVerification> f : fs) {
                assertThat(f.get(10, TimeUnit.SECONDS).isValid()).isTrue();
            }
            assertThat((System.nanoTime() - start) / 1_000_000).as("8 x 300ms calls overlap").isLessThan(2000L);
            assertThat(s.requests()).hasSize(8);
        }
    }

    @Test
    void cancelInterruptsTheBlockedCall() throws IOException, InterruptedException {
        try (ScriptedServer s = new ScriptedServer(Step.slow(4000, 200, "{}"))) {
            StampMitra c = StampMitra.builder().apiKey(TestClients.KEY).baseUrl(s.url()).maxRetries(0).build();
            CompletableFuture<?> f = c.usage().summaryAsync();
            long deadline = System.currentTimeMillis() + 3000;
            while (s.requests().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(s.requests()).hasSize(1);
            long start = System.nanoTime();
            assertThat(f.cancel(true)).isTrue();
            assertThat(f.isCancelled()).isTrue();
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(500L);
            Thread.sleep(300); // let the worker unwind; the pool must still be healthy
            assertThat(c.verify().panAsync("ABCDE1234F").isCancelled()).isFalse();
        }
    }
}
