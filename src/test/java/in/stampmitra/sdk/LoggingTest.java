package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import in.stampmitra.sdk.internal.Redactor;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggingTest {

    private static final String PAN_RESPONSE = "{\"referenceId\":\"SMV-1\",\"pan\":\"ABCDE1234F\",\"valid\":true,"
            + "\"registeredName\":\"RAHUL KUMAR\",\"dob\":\"1990-01-31\",\"address\":\"1 MG Road\"}";

    @Test
    void nothingIsLoggedByDefault() {
        FakeTransport t = new FakeTransport().then(200, PAN_RESPONSE);
        PrintStreamCapture capture = new PrintStreamCapture();
        try {
            TestClients.client(t).verify().pan("ABCDE1234F", "Rahul Kumar");
        } finally {
            capture.close();
        }
        assertThat(capture.text()).isEmpty();
    }

    @Test
    void debugLinesRedactCredentialsAndPersonalData() {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        FakeTransport t = new FakeTransport().then(200, PAN_RESPONSE);
        StampMitra c = TestClients.builder(t, TestClients.sleeps()).logger(lines::add).build();
        c.verify().pan("ABCDE1234F", "Rahul Kumar");
        String all = String.join("\n", lines);
        assertThat(lines).hasSize(2);
        assertThat(all).doesNotContain(TestClients.KEY).doesNotContain("abcdefgh12345678").contains("[REDACTED]")
                .doesNotContain("ABCDE1234F").doesNotContain("Rahul").doesNotContain("RAHUL")
                .doesNotContain("1990-01-31").doesNotContain("MG Road").contains("******234F").contains("SMV-1");
        assertThat(all).contains("-> POST https://api-b2b.stampmitra.in/v1/verify/pan attempt=1");
        assertThat(all).contains("<- 200 attempt=1");
    }

    @Test
    void debugFlagUsesTheSystemLogger() {
        FakeTransport t = new FakeTransport().then(200, "{}");
        TestClients.builder(t, TestClients.sleeps()).debug(true).build().usage().summary();
        assertThat(t.requests()).hasSize(1);
    }

    @Test
    void retriesAndErrorsAreLoggedWithoutLeaks() {
        List<String> lines = new ArrayList<>();
        FakeTransport t = new FakeTransport().thenThrow(new java.io.IOException("reset " + TestClients.KEY)).thenOk("{}");
        TestClients.builder(t, TestClients.sleeps()).logger(lines::add).build().usage().summary();
        assertThat(String.join("\n", lines)).doesNotContain(TestClients.KEY).contains("retrying in");
    }

    @Test
    void keyShapedStringsInBodiesAreMasked() {
        assertThat(Redactor.body("{\"note\":\"my key is sk_live_abcdefgh12345678 ok\"}")).doesNotContain("abcdefgh");
        assertThat(Redactor.body("<html>")).isEqualTo("[non-JSON body, 6 chars]");
        assertThat(Redactor.body("")).isEmpty();
        assertThat(Redactor.body("[{\"pan\":\"ABCDE1234F\"}]")).contains("******234F");
        assertThat(Redactor.body("{\"phone\":\"98\"}")).contains("****");
    }

    @Test
    void credentialHeadersAreRedacted() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer secret");
        h.put("x-api-key", "secret");
        h.put("Cookie", "a=b");
        h.put("Accept", "application/json");
        Map<String, String> out = Redactor.headers(h);
        assertThat(out.get("Authorization")).isEqualTo("[REDACTED]");
        assertThat(out.get("x-api-key")).isEqualTo("[REDACTED]");
        assertThat(out.get("Cookie")).isEqualTo("[REDACTED]");
        assertThat(out.get("Accept")).isEqualTo("application/json");
    }

    @Test
    void transportRequestToStringHidesHeaderValuesAndBody() {
        FakeTransport t = new FakeTransport().then(200, "{}");
        TestClients.client(t).verify().pan("ABCDE1234F");
        String s = t.last().toString();
        assertThat(s).doesNotContain(TestClients.KEY).doesNotContain("Bearer").doesNotContain("ABCDE1234F");
    }

    /** Captures System.out/err for the duration of a test. */
    private static final class PrintStreamCapture {
        private final java.io.PrintStream out = System.out;
        private final java.io.PrintStream err = System.err;
        private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

        PrintStreamCapture() {
            java.io.PrintStream p = new java.io.PrintStream(buffer, true);
            System.setOut(p);
            System.setErr(p);
        }

        String text() {
            return new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }

        void close() {
            System.setOut(out);
            System.setErr(err);
        }
    }
}
