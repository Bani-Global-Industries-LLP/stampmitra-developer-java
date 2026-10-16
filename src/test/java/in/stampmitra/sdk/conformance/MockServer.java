package in.stampmitra.sdk.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.internal.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/** Handle on the running spec-repo mock server. */
public final class MockServer implements AutoCloseable {

    private final Process process;
    private final String baseUrl;
    private final Path specDir;
    private final HttpClient http = HttpClient.newHttpClient();

    MockServer(Process process, String baseUrl, Path specDir) {
        this.process = process;
        this.baseUrl = baseUrl;
        this.specDir = specDir;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Path specDir() {
        return specDir;
    }

    /** Clears recorded requests and sequence counters. */
    public void reset() {
        try {
            http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/__mock/reset"))
                    .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("mock reset failed", e);
        }
    }

    /** Requests recorded since the last reset (method, path, query, headers (lower-case names), body). */
    public JsonNode requests() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/__mock/requests"))
                    .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
            return Json.mapper().readTree(r.body());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("mock requests failed", e);
        }
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
