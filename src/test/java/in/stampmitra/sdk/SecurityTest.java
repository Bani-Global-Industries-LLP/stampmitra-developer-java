package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.errors.StampMitraException;
import in.stampmitra.sdk.http.JdkHttpTransport;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.ScriptedServer;
import in.stampmitra.sdk.support.ScriptedServer.Step;
import in.stampmitra.sdk.support.TestClients;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class SecurityTest {

    private static String stackTrace(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    @Test
    void redirectsAreNeverFollowedAndNeverForwardTheKey() throws IOException {
        try (ScriptedServer evil = new ScriptedServer(Step.json(200, "{\"stolen\":true}"));
             ScriptedServer api = new ScriptedServer(Step.status(302, "").header("Location", evil.url() + "/v1/usage/summary"))) {
            StampMitra c = StampMitra.builder().apiKey(TestClients.KEY).baseUrl(api.url()).maxRetries(0).build();
            assertThatThrownBy(() -> c.usage().summary()).isInstanceOf(StampMitraException.class).satisfies(e -> {
                StampMitraException ex = (StampMitraException) e;
                assertThat(ex.getStatus()).isEqualTo(302);
                assertThat(ex.getMessage()).contains("never follows redirects");
                assertThat(stackTrace(ex)).doesNotContain(TestClients.KEY);
            });
            assertThat(evil.requests()).as("the redirect target must never be contacted").isEmpty();
            assertThat(api.requests()).hasSize(1);
        }
    }

    @Test
    void redirectsOnPostAreNotFollowedEither() throws IOException {
        try (ScriptedServer evil = new ScriptedServer(Step.json(200, "{}"));
             ScriptedServer api = new ScriptedServer(Step.status(307, "").header("Location", evil.url() + "/x"))) {
            StampMitra c = StampMitra.builder().apiKey(TestClients.KEY).baseUrl(api.url()).maxRetries(0).build();
            assertThatThrownBy(() -> c.verify().pan("ABCDE1234F")).isInstanceOf(StampMitraException.class);
            assertThat(evil.requests()).isEmpty();
        }
    }

    @Test
    void aRedirectFollowingHttpClientIsRefused() {
        HttpClient following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        assertThatThrownBy(() -> new JdkHttpTransport(following)).isInstanceOf(IllegalArgumentException.class);
        HttpClient normal = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        assertThatThrownBy(() -> new JdkHttpTransport(normal)).isInstanceOf(IllegalArgumentException.class);
        new JdkHttpTransport(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    @Test
    void theKeyNeverAppearsInExceptionTextOrStackTraces() {
        FakeTransport t = new FakeTransport().thenThrow(new IOException("reset by peer, header was Bearer " + TestClients.KEY));
        StampMitra c = TestClients.builder(t, TestClients.sleeps()).maxRetries(0).build();
        assertThatThrownBy(() -> c.usage().summary()).satisfies(e -> {
            assertThat(stackTrace(e)).doesNotContain(TestClients.KEY);
            assertThat(e.toString()).doesNotContain(TestClients.KEY);
        });
        FakeTransport rt = new FakeTransport().thenThrow(new IllegalStateException("bad " + TestClients.KEY));
        assertThatThrownBy(() -> TestClients.client(rt).usage().summary())
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(TestClients.KEY));
    }

    @Test
    void keyLeaksThroughTheCauseChainAreCut() {
        IOException inner = new IOException("inner " + TestClients.KEY);
        IOException outer = new IOException("outer", inner);
        FakeTransport t = new FakeTransport().thenThrow(outer);
        StampMitra c = TestClients.builder(t, TestClients.sleeps()).maxRetries(0).build();
        assertThatThrownBy(() -> c.usage().summary())
                .satisfies(e -> assertThat(stackTrace(e)).doesNotContain(TestClients.KEY));
    }

    @Test
    void theKeyIsSentOnlyInTheAuthorizationHeader() throws IOException {
        try (ScriptedServer api = new ScriptedServer(Step.json(200, "{}"))) {
            StampMitra c = StampMitra.builder().apiKey(TestClients.KEY).baseUrl(api.url()).build();
            c.verify().pan("ABCDE1234F");
            ScriptedServer.Recorded r = api.requests().get(0);
            assertThat(r.headers.get("authorization")).isEqualTo("Bearer " + TestClients.KEY);
            assertThat(r.path + r.query + r.body).doesNotContain(TestClients.KEY);
            assertThat(r.headers).doesNotContainKey("x-api-key");
        }
    }

    @Test
    void headerInjectionThroughPerRequestOptionsIsBlocked() {
        FakeTransport t = new FakeTransport().thenOk("{}");
        StampMitra c = TestClients.client(t);
        assertThatThrownBy(() -> c.usage().summary(RequestOptions.builder().header("X-Foo", "a\r\nX-Evil: 1").build()))
                .isInstanceOf(in.stampmitra.sdk.errors.ValidationException.class);
        assertThat(t.requests()).isEmpty();
    }

    @Test
    void plainHttpToARemoteHostCannotBeConfigured() {
        assertThatThrownBy(() -> StampMitra.builder().apiKey(TestClients.KEY).baseUrl("http://api-b2b.stampmitra.in").build())
                .isInstanceOf(in.stampmitra.sdk.errors.ValidationException.class);
    }
}
