package in.stampmitra.sdk.support;

import in.stampmitra.sdk.http.HttpTransport;
import in.stampmitra.sdk.http.TransportRequest;
import in.stampmitra.sdk.http.TransportResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scripted in-memory transport: replays queued responses (the last one repeats) and records requests. */
public final class FakeTransport implements HttpTransport {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<TransportRequest> requests = Collections.synchronizedList(new ArrayList<>());

    public FakeTransport then(int status, String body, String... headerPairs) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int i = 0; i < headerPairs.length; i += 2) {
            headers.put(headerPairs[i], Collections.singletonList(headerPairs[i + 1]));
        }
        script.add(new TransportResponse(status, headers, body == null ? null : body.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    public FakeTransport thenOk(String body) {
        return then(200, body);
    }

    public FakeTransport thenThrow(IOException e) {
        script.add(e);
        return this;
    }

    public FakeTransport thenThrow(RuntimeException e) {
        script.add(e);
        return this;
    }

    public List<TransportRequest> requests() {
        return requests;
    }

    public TransportRequest last() {
        return requests.get(requests.size() - 1);
    }

    @Override
    public TransportResponse send(TransportRequest request) throws IOException {
        requests.add(request);
        Object next = script.size() > 1 ? script.poll() : script.peek();
        if (next == null) {
            throw new IllegalStateException("FakeTransport has no scripted response");
        }
        if (next instanceof IOException) {
            throw (IOException) next;
        }
        if (next instanceof RuntimeException) {
            throw (RuntimeException) next;
        }
        return (TransportResponse) next;
    }
}
