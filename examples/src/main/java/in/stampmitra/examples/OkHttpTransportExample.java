package in.stampmitra.examples;

import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.http.HttpTransport;
import in.stampmitra.sdk.http.TransportRequest;
import in.stampmitra.sdk.http.TransportResponse;
import in.stampmitra.sdk.http.TransportTimeoutException;
import in.stampmitra.sdk.models.PanVerification;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Plugs OkHttp into the SDK through the {@link HttpTransport} interface. The two rules: never follow redirects (the
 * request carries the API key) and report timeouts as {@link TransportTimeoutException} or
 * {@code SocketTimeoutException}.
 */
public final class OkHttpTransportExample implements HttpTransport {

    private final OkHttpClient base;

    public OkHttpTransportExample(OkHttpClient base) {
        this.base = base.newBuilder().followRedirects(false).followSslRedirects(false).build();
    }

    @Override
    public TransportResponse send(TransportRequest request) throws IOException {
        Request.Builder builder = new Request.Builder().url(request.getUri().toString());
        request.getHeaders().forEach(builder::header);
        byte[] body = request.getBody();
        builder.method(request.getMethod(), body == null ? null
                : RequestBody.create(body, MediaType.parse("application/json")));
        // Per-attempt timeout covering the whole call.
        OkHttpClient client = base.newBuilder().callTimeout(request.getTimeout().toMillis(), TimeUnit.MILLISECONDS).build();
        try (Response response = client.newCall(builder.build()).execute()) {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            response.headers().names().forEach(n -> headers.put(n, new ArrayList<>(response.headers(n))));
            return new TransportResponse(response.code(), headers, response.body() == null ? null : response.body().bytes());
        } catch (InterruptedIOException e) {
            throw new TransportTimeoutException("OkHttp call timed out", e);
        }
    }

    public static void main(String[] args) {
        StampMitra.Builder builder = StampMitra.builder().apiKey(System.getenv("STAMPMITRA_API_KEY"))
                .transport(new OkHttpTransportExample(new OkHttpClient()));
        String baseUrl = System.getenv("STAMPMITRA_BASE_URL");
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }
        PanVerification pan = builder.build().verify().pan("ABCDE1234F");
        System.out.println("via OkHttp: valid=" + pan.isValid() + " reference=" + pan.getReferenceId());
    }
}
