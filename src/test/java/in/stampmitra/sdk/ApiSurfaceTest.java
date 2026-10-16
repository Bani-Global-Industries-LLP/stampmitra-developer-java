package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.internal.Json;
import in.stampmitra.sdk.models.Model;
import in.stampmitra.sdk.requests.CreateOrderRequest;
import in.stampmitra.sdk.requests.CreatePaymentIntentRequest;
import in.stampmitra.sdk.requests.EstampDutyCalculateRequest;
import in.stampmitra.sdk.requests.EstampPartiesRequest;
import in.stampmitra.sdk.resources.LogsParams;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Exercises the whole generated surface: every overload of every resource method (sync and async), and every typed
 * getter of every model against a body synthesised from the spec schema.
 */
class ApiSurfaceTest {

    private static final Map<String, String> SAMPLES = new LinkedHashMap<>();

    static {
        SAMPLES.put("pan", "ABCDE1234F");
        SAMPLES.put("gstin", "27AABCT1332L1ZZ");
        SAMPLES.put("businessName", "ACME");
        SAMPLES.put("vehicleNumber", "MH12AB1234");
        SAMPLES.put("voterId", "ABC1234567");
        SAMPLES.put("dlNumber", "MH1420110012345");
        SAMPLES.put("dob", "1990-01-31");
        SAMPLES.put("fileNumber", "PA1079341954215");
        SAMPLES.put("cin", "U74999DL2015PTC284009");
        SAMPLES.put("udyamNumber", "UDYAM-MH-12-1234567");
        SAMPLES.put("accountNumber", "000123456789");
        SAMPLES.put("ifsc", "ICIC0001234");
        SAMPLES.put("phone", "9876543210");
        SAMPLES.put("name", "Rahul");
    }

    private static Map<String, Object> duty() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stateCode", "MH");
        m.put("articleCode", "5");
        m.put("stampDutyAmount", 100);
        return m;
    }

    private static Map<String, Object> orderBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("externalOrderId", "o1");
        m.put("externalCustomerId", "c1");
        m.put("serviceCode", "s");
        return m;
    }

    private static Map<String, Object> paymentBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("externalOrderId", "o1");
        m.put("amountMinor", 100);
        m.put("currency", "INR");
        m.put("paymentConnectionId", "pc_1");
        return m;
    }

    private static Object sample(Parameter p, String methodName) {
        Class<?> t = p.getType();
        if (t == String.class) {
            return SAMPLES.getOrDefault(p.getName(), "x1");
        }
        if (t == Integer.class) {
            return 5;
        }
        if (t == RequestOptions.class) {
            return null;
        }
        if (t == Map.class) {
            return methodName.startsWith("calculateDuty") ? duty() : methodName.startsWith("create")
                    && p.getDeclaringExecutable().getDeclaringClass().getSimpleName().startsWith("Payment")
                    ? paymentBody() : methodName.startsWith("create") ? orderBody() : Collections.<String, Object>emptyMap();
        }
        if (t == CreateOrderRequest.class) {
            return CreateOrderRequest.builder().externalOrderId("o1").externalCustomerId("c1").serviceCode("s").build();
        }
        if (t == CreatePaymentIntentRequest.class) {
            return CreatePaymentIntentRequest.builder().externalOrderId("o1").amountMinor(1).currency("INR").paymentConnectionId("pc_1").build();
        }
        if (t == EstampDutyCalculateRequest.class) {
            return EstampDutyCalculateRequest.builder().stateCode("MH").articleCode("5").stampDutyAmount(1.5).build();
        }
        if (t == EstampPartiesRequest.class) {
            return EstampPartiesRequest.builder().customerName("A").build();
        }
        if (t == LogsParams.class) {
            return LogsParams.none();
        }
        throw new IllegalArgumentException("no sample for " + t);
    }

    @Test
    void everyResourceOverloadWorksSyncAndAsync() throws Exception {
        StampMitra c = TestClients.client(new FakeTransport().thenOk("{\"items\":[],\"nextCursor\":null}"));
        Object[] resources = {c.verify(), c.services(), c.estamp(), c.orders(), c.payments().intents(), c.usage(),
                c.webhooks().events(), c.logs()};
        int invoked = 0;
        for (Object r : resources) {
            for (Method m : r.getClass().getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || m.getName().startsWith("lambda$")
                        || m.getName().equals("iterate") || m.getName().equals("stream") || m.getName().equals("pages")) {
                    continue;
                }
                Object[] args = new Object[m.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = sample(m.getParameters()[i], m.getName());
                }
                Object result;
                try {
                    result = m.invoke(r, args);
                } catch (InvocationTargetException e) {
                    throw new AssertionError(r.getClass().getSimpleName() + "." + m.getName() + Arrays.toString(m.getParameterTypes()), e.getCause());
                }
                if (result instanceof CompletableFuture) {
                    result = ((CompletableFuture<?>) result).get(5, TimeUnit.SECONDS);
                }
                assertThat(result).as(m.toString()).isNotNull();
                invoked++;
            }
        }
        assertThat(invoked).isGreaterThan(150);
    }

    @Test
    void everySyncMethodHasAnAsyncTwinAndViceVersa() {
        StampMitra c = TestClients.client(new FakeTransport().thenOk("{}"));
        Object[] resources = {c.verify(), c.services(), c.estamp(), c.orders(), c.payments().intents(), c.usage(),
                c.webhooks().events()};
        for (Object r : resources) {
            TreeSet<String> sync = new TreeSet<>();
            TreeSet<String> async = new TreeSet<>();
            for (Method m : r.getClass().getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && !m.getName().startsWith("lambda$")) {
                    String sig = Arrays.toString(m.getParameterTypes());
                    if (m.getName().endsWith("Async")) {
                        async.add(m.getName().substring(0, m.getName().length() - 5) + sig);
                    } else {
                        sync.add(m.getName() + sig);
                    }
                }
            }
            assertThat(async).as(r.getClass().getSimpleName()).isEqualTo(sync);
        }
    }

    private static Object synth(JsonNode schemas, JsonNode prop, Map<String, Class<?>> models) {
        if (prop.has("$ref")) {
            String ref = prop.get("$ref").asText().substring(prop.get("$ref").asText().lastIndexOf('/') + 1);
            return synthObject(schemas, ref, models);
        }
        JsonNode typeNode = prop.get("type");
        String type = typeNode == null ? "any" : typeNode.isArray() ? typeNode.get(0).asText() : typeNode.asText();
        switch (type) {
            case "string":
                return prop.has("format") && prop.get("format").asText().equals("date-time") ? "2026-10-15T09:41:07.000Z" : "text";
            case "boolean":
                return true;
            case "integer":
                return 5;
            case "number":
                return 1.5;
            case "array":
                return Collections.singletonList(prop.has("items") ? synth(schemas, prop.get("items"), models) : "x");
            case "object":
                return Collections.singletonMap("k", "v");
            default:
                return "any";
        }
    }

    private static Map<String, Object> synthObject(JsonNode schemas, String name, Map<String, Class<?>> models) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : schemas.get(name).get("properties").properties()) {
            out.put(e.getKey(), synth(schemas, e.getValue(), models));
        }
        return out;
    }

    @Test
    void everyModelGetterReadsItsJsonKey() throws Exception {
        JsonNode schemas = Json.mapper().readTree(Files.readAllBytes(Paths.get(System.getProperty("project.basedir", "."),
                "spec/openapi.json"))).get("components").get("schemas");
        String[][] mapping = {{"PanVerificationResult", "PanVerification"}, {"GstinVerificationResult", "GstinVerification"},
                {"VehicleRcVerificationResult", "RcVerification"}, {"VoterIdVerificationResult", "VoterIdVerification"},
                {"DrivingLicenseVerificationResult", "DrivingLicenseVerification"},
                {"PassportVerificationResult", "PassportVerification"}, {"CinDirector", "CinDirector"},
                {"CinVerificationResult", "CinVerification"}, {"UdyamVerificationResult", "UdyamVerification"},
                {"PanToGstinEntry", "PanToGstinEntry"}, {"PanToGstinResult", "PanToGstin"}, {"PanToUdyamResult", "PanToUdyam"},
                {"IfscDetails", "IfscDetails"}, {"BankAccountVerificationResult", "BankAccountVerification"},
                {"Service", "Service"}, {"OrderReference", "OrderReference"}, {"OrderDetail", "OrderDetail"},
                {"OrderStatusResponse", "OrderStatusResponse"}, {"OrderPaymentResponse", "OrderPaymentResponse"},
                {"OrderDocumentsResponse", "OrderDocumentsResponse"}, {"DocumentDownloadUrl", "DocumentDownloadUrl"},
                {"PaymentIntent", "PaymentIntent"}, {"UsageSummary", "UsageSummary"}, {"LogEntry", "LogEntry"},
                {"LogsPage", "LogPage"}};
        int checked = 0;
        List<String> problems = new ArrayList<>();
        for (String[] m : mapping) {
            Class<?> cls = Class.forName("in.stampmitra.sdk.models." + m[1]);
            Map<String, Object> raw = synthObject(schemas, m[0], null);
            Model model = (Model) cls.getConstructor(Map.class).newInstance(raw);
            for (Map.Entry<String, JsonNode> prop : schemas.get(m[0]).get("properties").properties()) {
                String key = prop.getKey();
                String cap = Character.toUpperCase(key.charAt(0)) + key.substring(1);
                if (key.equals(key.toUpperCase())) {
                    cap = Character.toUpperCase(key.charAt(0)) + key.substring(1).toLowerCase();
                }
                Method getter = null;
                for (String candidate : new String[] {"get" + cap, "is" + cap, key}) {
                    try {
                        getter = cls.getMethod(candidate);
                        break;
                    } catch (NoSuchMethodException ignored) {
                        // try the next naming
                    }
                }
                if (getter == null) {
                    problems.add(m[1] + ": no getter for " + key);
                    continue;
                }
                Object value = getter.invoke(model);
                if (value == null || (value instanceof java.util.Collection && ((java.util.Collection<?>) value).isEmpty())
                        || Boolean.FALSE.equals(value)) {
                    problems.add(m[1] + "." + getter.getName() + " returned " + value + " for key " + key);
                }
                checked++;
            }
        }
        assertThat(problems).isEmpty();
        assertThat(checked).isGreaterThan(150);
    }

    @Test
    void requestBuildersExposeEveryDocumentedField() throws IOException {
        JsonNode schemas = Json.mapper().readTree(Files.readAllBytes(Paths.get(System.getProperty("project.basedir", "."),
                "spec/openapi.json"))).get("components").get("schemas");
        for (String name : new String[] {"CreateOrderRequest", "CreatePaymentIntentRequest", "EstampDutyCalculateRequest",
                "EstampPartiesRequest"}) {
            Class<?> builderClass = null;
            try {
                builderClass = Class.forName("in.stampmitra.sdk.requests." + name + "$Builder");
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            for (Map.Entry<String, JsonNode> p : schemas.get(name).get("properties").properties()) {
                boolean found = false;
                for (Method m : builderClass.getMethods()) {
                    found |= m.getName().equals(p.getKey());
                }
                assertThat(found).as("%s builder has %s()", name, p.getKey()).isTrue();
            }
        }
    }
}
