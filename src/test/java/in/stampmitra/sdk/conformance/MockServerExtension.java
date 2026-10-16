package in.stampmitra.sdk.conformance;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.opentest4j.TestAbortedException;

/**
 * Starts {@code scripts/mock-server.mjs} of the spec repo ({@code stampmitra-developer-openapi}) on a free port, once
 * per test run, and injects a {@link MockServer} into test methods and lifecycle methods.
 *
 * <p>Spec repo location: {@code STAMPMITRA_SPEC_DIR}, else {@code ../stampmitra-developer-openapi}, else
 * {@code /home/user/stampmitra-developer-openapi}. Node comes from {@code STAMPMITRA_NODE}, else {@code node} on the
 * PATH, else {@code /opt/node22/bin/node}. When something is missing the tests are skipped, unless
 * {@code CONFORMANCE_REQUIRED=1} (CI), where the run fails instead.
 */
public final class MockServerExtension implements BeforeAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(MockServerExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        get(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext p, ExtensionContext e) throws ParameterResolutionException {
        return p.getParameter().getType() == MockServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext p, ExtensionContext e) throws ParameterResolutionException {
        return get(e);
    }

    private static MockServer get(ExtensionContext context) {
        return context.getRoot().getStore(NS).getOrComputeIfAbsent("mock", k -> new Holder(start()), Holder.class).server;
    }

    private static boolean required() {
        return "1".equals(System.getenv("CONFORMANCE_REQUIRED"));
    }

    private static void unavailable(String why) {
        if (required()) {
            throw new IllegalStateException("conformance suite required but unavailable: " + why);
        }
        throw new TestAbortedException("conformance suite skipped: " + why);
    }

    static Path specDir() {
        String env = System.getenv("STAMPMITRA_SPEC_DIR");
        Path[] candidates = env != null && !env.isEmpty()
                ? new Path[] {Paths.get(env)}
                : new Path[] {Paths.get("../stampmitra-developer-openapi"), Paths.get("/home/user/stampmitra-developer-openapi")};
        for (Path c : candidates) {
            if (Files.isRegularFile(c.resolve("scripts/mock-server.mjs"))) {
                return c.toAbsolutePath().normalize();
            }
        }
        unavailable("spec repo not found (set STAMPMITRA_SPEC_DIR)");
        return null;
    }

    private static String node() {
        String env = System.getenv("STAMPMITRA_NODE");
        if (env != null && !env.isEmpty()) {
            return env;
        }
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Paths.get(dir, "node"))) {
                return Paths.get(dir, "node").toString();
            }
        }
        if (Files.isExecutable(Paths.get("/opt/node22/bin/node"))) {
            return "/opt/node22/bin/node";
        }
        unavailable("node not found");
        return null;
    }

    private static MockServer start() {
        Path spec = specDir();
        String node = node();
        try {
            int port;
            try (ServerSocket s = new ServerSocket(0)) {
                port = s.getLocalPort();
            }
            Process process = new ProcessBuilder(node, spec.resolve("scripts/mock-server.mjs").toString(), "--port",
                    String.valueOf(port), "--host", "127.0.0.1").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            String base = "http://127.0.0.1:" + port;
            HttpClient http = HttpClient.newHttpClient();
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive()) {
                    unavailable("mock server exited with " + process.exitValue());
                }
                try {
                    HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/__mock/health"))
                            .timeout(Duration.ofSeconds(1)).build(), HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() == 200) {
                        return new MockServer(process, base, spec);
                    }
                } catch (IOException e) {
                    Thread.sleep(100);
                }
            }
            process.destroyForcibly();
            unavailable("mock server did not become healthy");
        } catch (IOException e) {
            unavailable("cannot start the mock server: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            unavailable("interrupted");
        }
        return null;
    }

    /** Closed by JUnit at the end of the whole run. */
    private static final class Holder implements AutoCloseable {
        final MockServer server;

        Holder(MockServer server) {
            this.server = server;
        }

        @Override
        public void close() {
            server.close();
        }
    }
}
