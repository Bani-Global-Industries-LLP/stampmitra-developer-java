package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.models.BankAccountVerification;
import in.stampmitra.sdk.models.CinVerification;
import in.stampmitra.sdk.models.GenericResponse;
import in.stampmitra.sdk.models.GstinVerification;
import in.stampmitra.sdk.models.LogPage;
import in.stampmitra.sdk.models.OrderReference;
import in.stampmitra.sdk.models.PanToGstin;
import in.stampmitra.sdk.models.PanVerification;
import in.stampmitra.sdk.models.PaymentIntent;
import in.stampmitra.sdk.models.Service;
import in.stampmitra.sdk.models.ServiceList;
import in.stampmitra.sdk.requests.CreateOrderRequest;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelTest {

    private static StampMitra respond(String json) {
        return TestClients.client(new FakeTransport().then(200, json));
    }

    @Test
    void unknownFieldsAreIgnoredByGettersButPreserved() {
        PanVerification pan = respond("{\"referenceId\":\"SMV-1\",\"pan\":\"ABCDE1234F\",\"valid\":true,"
                + "\"registeredName\":\"ACME\",\"brandNewField\":{\"x\":[1,2,{\"y\":null}]}}").verify().pan("ABCDE1234F");
        assertThat(pan.getReferenceId()).isEqualTo("SMV-1");
        assertThat(pan.isValid()).isTrue();
        assertThat(pan.getRegisteredName()).isEqualTo("ACME");
        assertThat(pan.getFatherName()).isNull();
        assertThat(pan.get("brandNewField")).isInstanceOf(Map.class);
        assertThat(pan.getRaw()).containsKey("brandNewField");
        assertThat(pan.toJson()).contains("brandNewField");
    }

    @Test
    void modelsAreDeeplyImmutable() {
        PanVerification pan = respond("{\"valid\":true,\"extra\":{\"list\":[1,2]}}").verify().pan("ABCDE1234F");
        assertThatThrownBy(() -> pan.getRaw().put("valid", false)).isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pan.get("extra");
        assertThatThrownBy(() -> extra.put("a", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((java.util.List<?>) extra.get("list")).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void gstinResponseKeyIsUpperCase() {
        GstinVerification g = respond("{\"referenceId\":\"r\",\"GSTIN\":\"27AABCT1332L1ZZ\",\"valid\":false,\"legalName\":\"X\"}")
                .verify().gstin("27AABCT1332L1ZZ");
        assertThat(g.getGstin()).isEqualTo("27AABCT1332L1ZZ");
        assertThat(g.isValid()).isFalse();
    }

    @Test
    void nestedModelsAndListsAreTyped() {
        CinVerification cin = respond("{\"referenceId\":\"r\",\"valid\":true,\"directors\":[{\"din\":\"01234567\",\"name\":\"A\","
                + "\"designation\":\"Director\"}]}").verify().cin("U74999DL2015PTC284009");
        assertThat(cin.getDirectors()).hasSize(1);
        assertThat(cin.getDirectors().get(0).getDin()).isEqualTo("01234567");

        BankAccountVerification bank = respond("{\"referenceId\":\"r\",\"valid\":true,\"ifscDetails\":{\"bank\":\"ICICI\","
                + "\"ifsc\":\"ICIC0001234\"}}").verify().bankAccount("123456789", "ICIC0001234");
        assertThat(bank.getIfscDetails().getBank()).isEqualTo("ICICI");

        PanToGstin p = respond("{\"referenceId\":\"r\",\"found\":true,\"gstins\":[{\"gstin\":\"27AABCT1332L1ZZ\",\"state\":\"MH\","
                + "\"status\":\"Active\"}]}").verify().panToGstin("ABCDE1234F");
        assertThat(p.getGstins().get(0).getState()).isEqualTo("MH");
        assertThat(p.isFound()).isTrue();
        assertThat(bank.getIfscDetails().getBranch()).isNull();
        assertThat(respond("{\"referenceId\":\"r\",\"valid\":true}").verify().bankAccount("123456789", "ICIC0001234")
                .getIfscDetails()).isNull();
        assertThat(respond("{\"referenceId\":\"r\",\"valid\":true}").verify().cin("U74999DL2015PTC284009")
                .getDirectors()).isEmpty();
    }

    @Test
    void numbersAndDatesAreTyped() {
        PaymentIntent pi = respond("{\"id\":\"pi_1\",\"externalOrderId\":\"o\",\"coreOrderId\":null,\"amountMinor\":5000000000,"
                + "\"currency\":\"INR\",\"status\":\"CREATED\",\"providerIntentId\":null,\"createdAt\":\"2026-10-15T09:41:07.000Z\"}")
                .payments().intents().get("pi_1");
        assertThat(pi.getAmountMinor()).isEqualTo(5_000_000_000L);
        assertThat(pi.getCreatedAt()).isEqualTo(Instant.parse("2026-10-15T09:41:07Z"));
        assertThat(pi.getCoreOrderId()).isNull();
        PaymentIntent bad = respond("{\"createdAt\":\"yesterday\"}").payments().intents().get("pi_1");
        assertThat(bad.getCreatedAt()).isNull();
        assertThat(bad.get("createdAt")).isEqualTo("yesterday");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("basePrice", "500.00");
        assertThat(new Service(raw).getBasePrice()).isEqualTo("500.00");
        assertThat(new Service(Collections.<String, Object>singletonMap("x", new BigDecimal("1.5"))).getRaw())
                .containsKey("x");
    }

    @Test
    void ordersUseExternalOrderId() {
        OrderReference o = respond("{\"externalOrderId\":\"ext_1\",\"createdAt\":\"2026-10-15T09:41:07.000Z\",\"serviceCode\":\"s\"}")
                .orders().create(CreateOrderRequest.builder().externalOrderId("ext_1").externalCustomerId("c").serviceCode("s").build());
        assertThat(o.getExternalOrderId()).isEqualTo("ext_1");
    }

    @Test
    void bareArrayServicesAreWrapped() {
        ServiceList list = respond("[{\"serviceCode\":\"a\",\"name\":\"A\",\"isActive\":true,\"entitled\":true},{\"serviceCode\":\"b\"}]")
                .services().list();
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getServices().get(0).getServiceCode()).isEqualTo("a");
        assertThat(list.getServices().get(0).isActive()).isTrue();
        assertThat(list.getServices().get(1).isActive()).isFalse();
        int n = 0;
        for (Service s : list) {
            n += s.getServiceCode() == null ? 0 : 1;
        }
        assertThat(n).isEqualTo(2);
    }

    @Test
    void genericResponsesKeepEverything() {
        GenericResponse g = respond("[{\"code\":\"MH\"},{\"code\":\"DL\"}]").estamp().listStates();
        assertThat(g.getItems()).hasSize(2);
        GenericResponse obj = respond("{\"states\":[1],\"x\":2}").estamp().listStates();
        assertThat(obj.get("x")).isEqualTo(2);
        assertThat(obj.getItems()).isEmpty();
        assertThat(respond("").estamp().listStates().getRaw()).isEmpty();
    }

    @Test
    void logPagesExposeItemsAndCursor() {
        LogPage page = respond("{\"items\":[{\"id\":\"1\",\"createdAt\":\"2026-10-15T09:41:07.000Z\",\"service\":\"pan.verify\","
                + "\"metadata\":{\"k\":\"v\"}}],\"nextCursor\":\"c1\"}").logs().list();
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getNextCursor()).isEqualTo("c1");
        assertThat(page.getItems().get(0).getMetadata()).containsEntry("k", "v");
    }

    @Test
    void toStringMasksPersonalData() {
        PanVerification pan = respond("{\"referenceId\":\"SMV-1\",\"pan\":\"ABCDE1234F\",\"valid\":true,"
                + "\"registeredName\":\"RAHUL KUMAR\",\"fatherName\":\"X Y\"}").verify().pan("ABCDE1234F");
        assertThat(pan.toString()).contains("SMV-1").contains("******234F").doesNotContain("ABCDE1234F")
                .doesNotContain("RAHUL").doesNotContain("X Y");
    }

    @Test
    void equalityIsByContent() {
        Map<String, Object> m = Collections.<String, Object>singletonMap("valid", true);
        assertThat(new PanVerification(m)).isEqualTo(new PanVerification(m)).hasSameHashCodeAs(new PanVerification(m));
        assertThat(new PanVerification(m)).isNotEqualTo(new GstinVerification(m));
        assertThat(new PanVerification(null).getRaw()).isEmpty();
    }
}
