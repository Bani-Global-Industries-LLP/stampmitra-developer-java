package in.stampmitra.sdk.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default {@link HttpTransport} built on {@code java.net.http.HttpClient} (no extra dependency).
 *
 * <p>Redirects are never followed, the system {@link ProxySelector} is honoured, and the per-attempt timeout covers
 * the whole exchange including the response body.
 */
public final class JdkHttpTransport implements HttpTransport {

    private final HttpClient client;

    /** Creates a transport with a fresh {@code HttpClient} that never follows redirects. */
    public JdkHttpTransport() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    /**
     * Wraps an existing client, for example to configure an {@code SSLContext}, an authenticated proxy or an executor.
     *
     * @param client a client whose redirect policy is {@link HttpClient.Redirect#NEVER}
     * @throws IllegalArgumentException if the client would follow redirects (it could leak the API key)
     */
    public JdkHttpTransport(HttpClient client) {
        Objects.requireNonNull(client, "client");
        if (client.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException(
                    "HttpClient must be built with followRedirects(NEVER): redirects could forward the API key to another host");
        }
        this.client = client;
    }

    @Override
    public TransportResponse send(TransportRequest request) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.getUri()).timeout(request.getTimeout());
        for (Map.Entry<String, String> h : request.getHeaders().entrySet()) {
            builder.header(h.getKey(), h.getValue());
        }
        byte[] body = request.getBody();
        builder.method(request.getMethod(), body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));

        CompletableFuture<HttpResponse<byte[]>> future =
                client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        try {
            // HttpRequest.timeout() ends at the response headers; this bounds the body read as well.
            HttpResponse<byte[]> response =
                    future.get(request.getTimeout().toMillis() + 1000L, TimeUnit.MILLISECONDS);
            Map<String, List<String>> headers = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : response.headers().map().entrySet()) {
                headers.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            return new TransportResponse(response.statusCode(), headers, response.body());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new HttpTimeoutException("request timed out after " + request.getTimeout());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("interrupted while waiting for the response");
            interrupted.initCause(e);
            throw interrupted;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IOException(cause);
        }
    }
}
