package in.stampmitra.examples;

import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.models.LogEntry;
import in.stampmitra.sdk.resources.LogsParams;

/** Walks all request logs: the iterator follows {@code nextCursor} page by page, lazily. */
public final class LogsPagination {

    private LogsPagination() {
    }

    public static void main(String[] args) {
        StampMitra client = Examples.client();
        LogsParams params = LogsParams.builder().service("pan.verify").status("success").limit(100).build();

        int count = 0;
        for (LogEntry entry : client.logs().iterate(params)) {
            System.out.println(entry.getId() + " " + entry.getCreatedAt() + " " + entry.getService());
            count++;
        }
        System.out.println(count + " log entries");

        // The same as a java.util.stream.Stream (stops fetching pages as soon as the limit is reached).
        long recent = client.logs().stream(params).limit(5).count();
        System.out.println("first five: " + recent);
    }
}
