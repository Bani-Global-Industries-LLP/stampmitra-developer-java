package in.stampmitra.sdk.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loopback HTTP server that replays a script of steps, for exact control over statuses, headers, bodies, dropped
 * connections and slow responses. The last step repeats.
 */
public final class ScriptedServer implements AutoCloseable {

    /** One scripted reaction. */
    public static final class Step {
        final int status;
        final Map<String, String> headers = new LinkedHashMap<>();
        final String body;
        final boolean drop;
        final long delayMillis;

        private Step(int status, String body, boolean drop, long delayMillis) {
            this.status = status;
            this.body = body;
            this.drop = drop;
            this.delayMillis = delayMillis;
        }

        public static Step status(int status, String body) {
            return new Step(status, body, false, 0);
        }

        public static Step json(int status, String body) {
            return new Step(status, body, false, 0).header("Content-Type", "application/json");
        }

        public static Step drop() {
            return new Step(0, null, true, 0);
        }

        public static Step slow(long millis, int status, String body) {
            return new Step(status, body, false, millis).header("Content-Type", "application/json");
        }

        public Step header(String name, String value) {
            headers.put(name, value);
            return this;
        }
    }

    /** A request the server received. */
    public static final class Recorded {
        public final String method;
        public final String path;
        public final String query;
        public final Map<String, String> headers = new LinkedHashMap<>();
        public final String body;

        Recorded(String method, String path, String query, String body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.body = body;
        }
    }

    private final HttpServer server;
    private final List<Step> steps = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final List<Recorded> recorded = Collections.synchronizedList(new ArrayList<>());

    public ScriptedServer(Step... script) throws IOException {
        Collections.addAll(steps, script);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] in;
            try (InputStream is = exchange.getRequestBody()) {
                in = is.readAllBytes();
            }
            Recorded r = new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                    exchange.getRequestURI().getRawQuery(), new String(in, StandardCharsets.UTF_8));
            exchange.getRequestHeaders().forEach((k, v) -> r.headers.put(k.toLowerCase(java.util.Locale.ROOT), v.get(0)));
            recorded.add(r);
            Step step = steps.get(Math.min(counter.getAndIncrement(), steps.size() - 1));
            if (step.drop) {
                exchange.close();
                return;
            }
            if (step.delayMillis > 0) {
                try {
                    Thread.sleep(step.delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            step.headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            byte[] out = step.body == null ? new byte[0] : step.body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(step.status, out.length == 0 ? -1 : out.length);
                if (out.length > 0) {
                    exchange.getResponseBody().write(out);
                }
            } catch (IOException ignored) {
                // client went away (timeout tests)
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "scripted-server");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Recorded> requests() {
        return recorded;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
