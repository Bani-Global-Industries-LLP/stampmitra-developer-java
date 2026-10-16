package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.internal.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fails when an operation of the bundled spec snapshot (spec/openapi.json) has no SDK method, or the SDK calls an
 * operation the spec does not have. Run alone with {@code mvn -q test -Dtest=SpecCoverageTest}.
 */
class SpecCoverageTest {

    private static final Path ROOT = Paths.get(System.getProperty("project.basedir", "."));

    static Set<String> specOperations() throws IOException {
        JsonNode spec = Json.mapper().readTree(Files.readAllBytes(ROOT.resolve("spec/openapi.json")));
        Set<String> ops = new TreeSet<>();
        for (Map.Entry<String, JsonNode> p : spec.get("paths").properties()) {
            for (String method : new String[] {"get", "post", "put", "patch", "delete"}) {
                if (p.getValue().has(method)) {
                    ops.add(method.toUpperCase() + " " + p.getKey().replaceAll("\\{[^}]+\\}", "{}"));
                }
            }
        }
        return ops;
    }

    static Set<String> sdkOperations() throws IOException {
        Pattern p = Pattern.compile("Operation\\.(get|post|put|patch|delete)\\(\"([^\"]+)\"\\)");
        Set<String> ops = new TreeSet<>();
        try (Stream<Path> files = Files.walk(ROOT.resolve("src/main/java/in/stampmitra/sdk/resources"))) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                Matcher m = p.matcher(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
                while (m.find()) {
                    ops.add(m.group(1).toUpperCase() + " " + m.group(2).replaceAll("\\{[^}]+\\}", "{}"));
                }
            }
        }
        return ops;
    }

    @Test
    void everySpecOperationHasAnSdkMethod() throws IOException {
        Set<String> missing = new TreeSet<>(specOperations());
        missing.removeAll(sdkOperations());
        assertThat(missing).as("spec operations without an SDK method").isEmpty();
    }

    @Test
    void theSdkCallsNothingTheSpecLacks() throws IOException {
        Set<String> extra = new TreeSet<>(sdkOperations());
        extra.removeAll(specOperations());
        assertThat(extra).as("SDK operations missing from spec/openapi.json").isEmpty();
    }

    @Test
    void thereAreThirtyOneOperations() throws IOException {
        assertThat(specOperations()).hasSize(31);
        assertThat(sdkOperations()).hasSize(31);
    }
}
