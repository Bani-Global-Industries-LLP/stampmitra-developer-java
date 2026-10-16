package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.errors.AuthenticationException;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import in.stampmitra.sdk.http.TransportRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientTest {

    private static StampMitra.Builder base() {
        return StampMitra.builder().apiKey(TestClients.KEY).transport(new FakeTransport().thenOk("{}"));
    }

    @Test
    void versionMatchesThePom() {
        assertThat(StampMitra.VERSION).isEqualTo(System.getProperty("project.version", StampMitra.VERSION));
    }

    @Test
    void environmentIsDerivedFromTheKey() {
        assertThat(base().build().environment()).isEqualTo("test");
        assertThat(base().apiKey("sk_live_abcdefgh12345678").build().environment()).isEqualTo("live");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "sk_test_short", "pk_test_abcdefgh12345678", "sk_prod_abcdefgh12345678",
            "sk_test_abcdefgh12345678\nX-Evil: 1", "sk_test_abcdefgh 12345678", "Bearer sk_test_abcdefgh12345678"})
    void malformedKeysAreRejectedWithoutEchoingThem(String key) {
        Assumptions.assumeTrue(System.getenv(StampMitra.API_KEY_ENV) == null);
        assertThatThrownBy(() -> base().apiKey(key).build())
                .isInstanceOf(AuthenticationException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("abcdefgh"));
    }

    @Test
    void customKeysNeedOptIn() {
        StampMitra c = base().apiKey("custom-key-123456").allowCustomKey(true).build();
        assertThat(c.environment()).isEqualTo("custom");
        assertThatThrownBy(() -> base().apiKey("custom key with spaces").allowCustomKey(true).build())
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void missingKeyFailsClearly() {
        Assumptions.assumeTrue(System.getenv(StampMitra.API_KEY_ENV) == null);
        assertThatThrownBy(() -> StampMitra.builder().build())
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("STAMPMITRA_API_KEY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://api-b2b.stampmitra.in", "https://api-b2b.stampmitra.in/", "http://localhost:4010",
            "http://127.0.0.1:4010", "http://[::1]:4010", "https://example.com/base/path"})
    void acceptableBaseUrls(String url) {
        assertThat(base().baseUrl(url).build().baseUrl()).doesNotEndWith("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://api-b2b.stampmitra.in", "http://example.com", "http://localhost.evil.com",
            "http://127.0.0.1.evil.com", "ftp://example.com", "api-b2b.stampmitra.in", "https://", "",
            "https://user:pass@api-b2b.stampmitra.in", "https://evil.com@api-b2b.stampmitra.in",
            "https://api-b2b.stampmitra.in?x=1", "https://api-b2b.stampmitra.in#frag", "https://exa mple.com",
            "http://localhost@evil.com"})
    void unsafeBaseUrlsAreRejected(String url) {
        assertThatThrownBy(() -> base().baseUrl(url).build()).isInstanceOf(ValidationException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThatThrownBy(() -> base().timeout(java.time.Duration.ZERO).build()).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> base().maxRetries(-1).build()).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> base().defaultHeader("Authorization", "Bearer x").build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> base().defaultHeader("X-A", "v\r\nX-B: 1").build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> base().defaultHeader("Bad Name", "v").build()).isInstanceOf(ValidationException.class);
    }

    @Test
    void sendsTheStandardHeaders() {
        FakeTransport t = new FakeTransport().thenOk("{}");
        StampMitra c = base().transport(t).appInfo("my-app", "1.2.3", "https://example.com")
                .defaultHeader("X-Tenant", "acme").build();
        c.usage().summary();
        TransportRequest r = t.last();
        assertThat(r.getMethod()).isEqualTo("GET");
        assertThat(r.getUri().toString()).isEqualTo("https://api-b2b.stampmitra.in/v1/usage/summary");
        assertThat(r.getHeaders()).containsEntry("Authorization", "Bearer " + TestClients.KEY)
                .containsEntry("Accept", "application/json").containsEntry("Content-Type", "application/json")
                .containsEntry("X-Tenant", "acme");
        assertThat(r.getHeaders().get("User-Agent")).startsWith("stampmitra-java/0.1.0 (Java ")
                .endsWith("my-app/1.2.3 (https://example.com)");
        assertThat(r.getBody()).isNull();
    }

    @Test
    void toStringsNeverContainTheKey() {
        StampMitra c = base().build();
        assertThat(c.toString()).doesNotContain("abcdefgh").contains("environment=test");
        assertThat(c.verify().toString()).doesNotContain("abcdefgh");
        assertThat(RequestOptions.builder().idempotencyKey("secret-idem").build().toString())
                .doesNotContain("secret-idem");
    }

    @Test
    void perRequestHeadersCannotOverrideCredentials() {
        StampMitra c = base().build();
        RequestOptions evil = RequestOptions.builder().header("X-Api-Key", "other").build();
        assertThatThrownBy(() -> c.usage().summary(evil)).isInstanceOf(ValidationException.class);
        RequestOptions evil2 = RequestOptions.builder().header("authorization", "Bearer other").build();
        assertThatThrownBy(() -> c.usage().summary(evil2)).isInstanceOf(ValidationException.class);
    }
}
