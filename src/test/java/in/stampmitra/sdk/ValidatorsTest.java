package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import in.stampmitra.sdk.errors.ValidationException;
import in.stampmitra.sdk.internal.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ValidatorsTest {

    private static JsonNode vectors() throws IOException {
        try (InputStream in = ValidatorsTest.class.getResourceAsStream("/fixtures/validators.json")) {
            return Json.mapper().readTree(in).get("validators");
        }
    }

    private static Function<String, String> validator(String name) {
        switch (name) {
            case "pan": return Validators::pan;
            case "gstin": return Validators::gstin;
            case "ifsc": return Validators::ifsc;
            case "cin": return Validators::cin;
            case "udyam": return Validators::udyam;
            case "vehicleNumber": return Validators::vehicleNumber;
            case "bankAccount": return Validators::bankAccount;
            case "passport": return Validators::passportFileNumber;
            case "drivingLicense": return Validators::drivingLicense;
            case "voterId": return Validators::voterId;
            default: throw new IllegalArgumentException(name);
        }
    }

    @TestFactory
    List<DynamicTest> sharedVectors() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        JsonNode all = vectors();
        all.fieldNames().forEachRemaining(name -> {
            Function<String, String> fn = validator(name);
            for (JsonNode valid : all.get(name).get("valid")) {
                tests.add(DynamicTest.dynamicTest(name + " valid " + valid.asText(), () -> {
                    String out = fn.apply(valid.asText());
                    // trim + uppercase (bank account: trim only), never strip separators
                    String expected = name.equals("bankAccount") ? valid.asText().trim()
                            : valid.asText().trim().toUpperCase(Locale.ROOT);
                    assertThat(out).isEqualTo(expected);
                }));
            }
            for (JsonNode invalid : all.get(name).get("invalid")) {
                tests.add(DynamicTest.dynamicTest(name + " invalid '" + invalid.asText() + "'",
                        () -> assertThatThrownBy(() -> fn.apply(invalid.asText()))
                                .isInstanceOf(ValidationException.class)));
            }
        });
        assertThat(tests.size()).isGreaterThan(100);
        return tests;
    }

    @Test
    void patternsMatchTheVectorFile() throws IOException {
        JsonNode all = vectors();
        assertThat(Validators.PAN.pattern()).isEqualTo(all.get("pan").get("pattern").asText());
        assertThat(Validators.GSTIN.pattern()).isEqualTo(all.get("gstin").get("pattern").asText());
        assertThat(Validators.IFSC.pattern()).isEqualTo(all.get("ifsc").get("pattern").asText());
        assertThat(Validators.CIN.pattern()).isEqualTo(all.get("cin").get("pattern").asText());
        assertThat(Validators.UDYAM.pattern()).isEqualTo(all.get("udyam").get("pattern").asText());
        assertThat(Validators.VEHICLE_NUMBER.pattern()).isEqualTo(all.get("vehicleNumber").get("pattern").asText());
        assertThat(Validators.BANK_ACCOUNT.pattern()).isEqualTo(all.get("bankAccount").get("pattern").asText());
        assertThat(Validators.PASSPORT_FILE_NUMBER.pattern()).isEqualTo(all.get("passport").get("pattern").asText());
        assertThat(Validators.DRIVING_LICENSE.pattern()).isEqualTo(all.get("drivingLicense").get("pattern").asText());
        assertThat(Validators.VOTER_ID.pattern()).isEqualTo(all.get("voterId").get("pattern").asText());
    }

    @Test
    void trailingNewlinesDoNotSlipThroughTheAnchor() {
        assertThatThrownBy(() -> Validators.pan("ABCDE1234F\nX")).isInstanceOf(ValidationException.class);
        assertThat(Validators.pan("ABCDE1234F\n")).isEqualTo("ABCDE1234F");
        assertThatThrownBy(() -> Validators.bankAccount("123456789\n123")).isInstanceOf(ValidationException.class);
    }

    @Test
    void unicodeDigitsAreNotAccepted() {
        assertThatThrownBy(() -> Validators.bankAccount("١٢٣٤٥٦٧٨٩")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Validators.pan("ABCDE１２３４F")).isInstanceOf(ValidationException.class);
    }

    @Test
    void phoneDateAndNameHelpers() {
        for (String ok : new String[] {"9876543210", "+919876543210", "919876543210", "09876543210"}) {
            assertThat(Validators.phone(ok)).isEqualTo(ok);
        }
        for (String bad : new String[] {"", "1234567890", "98765", "+9198765432100", null}) {
            assertThatThrownBy(() -> Validators.phone(bad)).isInstanceOf(ValidationException.class);
        }
        assertThat(Validators.date("1990-01-31", "dob")).isEqualTo("1990-01-31");
        assertThat(Validators.date("1990-01-31T00:00:00Z", "dob")).isEqualTo("1990-01-31T00:00:00Z");
        for (String bad : new String[] {"31-01-1990", "1990-02-30", "1990-1-1", "", null, "abcd-ef-gh"}) {
            assertThatThrownBy(() -> Validators.date(bad, "dob")).isInstanceOf(ValidationException.class);
        }
        assertThat(Validators.name("  Rahul  ", "name")).isEqualTo("Rahul");
        assertThatThrownBy(() -> Validators.name("  ", "name")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Validators.name(new String(new char[257]).replace('\0', 'a'), "name"))
                .isInstanceOf(ValidationException.class);
    }
}
