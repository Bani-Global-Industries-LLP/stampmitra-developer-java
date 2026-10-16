package in.stampmitra.sdk.support;

import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.http.HttpTransport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds clients that never sleep and never touch the network. */
public final class TestClients {

    public static final String KEY = "sk_test_abcdefgh12345678";

    private TestClients() {
    }

    public static StampMitra.Builder builder(HttpTransport transport, List<Duration> sleeps) {
        return StampMitra.builder().apiKey(KEY).transport(transport).sleeper(sleeps::add);
    }

    public static StampMitra client(HttpTransport transport) {
        return builder(transport, new ArrayList<>()).build();
    }

    public static List<Duration> sleeps() {
        return Collections.synchronizedList(new ArrayList<>());
    }
}
