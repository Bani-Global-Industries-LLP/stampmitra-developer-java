package in.stampmitra.sdk.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.internal.Json;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The per-operation fixtures of the spec repo ({@code fixtures/operations/*.json}). */
final class SpecFixtures {

    /** One recorded request/response pair. */
    static final class Fixture {
        final String file;
        final String method;
        final String template;
        final String scenario;
        final JsonNode request;
        final int status;
        final JsonNode body;

        Fixture(String file, JsonNode json) {
            this.file = file;
            this.method = json.get("operation").get("method").asText();
            this.template = json.get("operation").get("path").asText();
            this.scenario = json.get("scenario").asText();
            this.request = json.get("request");
            this.status = json.get("response").get("status").asInt();
            this.body = json.get("response").get("body");
        }
    }

    private SpecFixtures() {
    }

    static List<Fixture> load(Path specDir) throws IOException {
        List<Fixture> out = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(specDir.resolve("fixtures/operations"), "*.json")) {
            for (Path f : files) {
                out.add(new Fixture(f.getFileName().toString(), Json.mapper().readTree(Files.readAllBytes(f))));
            }
        }
        out.sort(Comparator.comparing((Fixture f) -> f.file));
        return out;
    }

    static Fixture success(List<Fixture> all, String method, String template) {
        for (Fixture f : all) {
            if (f.method.equals(method) && f.template.equals(template) && f.scenario.equals("success")) {
                return f;
            }
        }
        throw new IllegalStateException("no success fixture for " + method + " " + template);
    }
}
