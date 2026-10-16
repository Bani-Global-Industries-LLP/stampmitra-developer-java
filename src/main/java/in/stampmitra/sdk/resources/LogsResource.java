package in.stampmitra.sdk.resources;

import in.stampmitra.sdk.RequestOptions;
import in.stampmitra.sdk.errors.StampMitraException;
import in.stampmitra.sdk.internal.Operation;
import in.stampmitra.sdk.internal.RequestExecutor;
import in.stampmitra.sdk.models.LogEntry;
import in.stampmitra.sdk.models.LogPage;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Request logs ({@code client.logs()}) with cursor pagination.
 *
 * <pre>{@code
 * for (LogEntry entry : client.logs().iterate(LogsParams.builder().service("pan.verify").limit(100).build())) {
 *     System.out.println(entry.getId());
 * }
 * }</pre>
 */
public final class LogsResource {

    private static final Operation GET_V1_LOGS = Operation.get("/v1/logs");

    private final RequestExecutor executor;
    private final boolean validateInput;

    /**
     * Creates the resource. Use {@link in.stampmitra.sdk.StampMitra#logs()} instead.
     *
     * @param executor      the shared request executor
     * @param validateInput whether to validate inputs locally before sending
     */
    public LogsResource(RequestExecutor executor, boolean validateInput) {
        this.executor = executor;
        this.validateInput = validateInput;
    }

    /**
     * Fetches the first page of logs with the server's default page size.
     *
     * @return one page
     * @throws StampMitraException on API, validation or network errors
     */
    public LogPage list() {
        return list(LogsParams.none(), null);
    }

    /**
     * Fetches one page of logs.
     *
     * @param params filters, page size and cursor
     * @return one page ({@code getNextCursor()} is {@code null} on the last page)
     * @throws StampMitraException on API, validation or network errors
     */
    public LogPage list(LogsParams params) {
        return list(params, null);
    }

    /**
     * Fetches one page of logs.
     *
     * @param params  filters, page size and cursor
     * @param options per-request overrides, may be {@code null}
     * @return one page ({@code getNextCursor()} is {@code null} on the last page)
     * @throws StampMitraException on API, validation or network errors
     */
    public LogPage list(LogsParams params, RequestOptions options) {
        LogsParams p = Objects.requireNonNull(params, "params");
        if (validateInput) {
            p.validate();
        }
        return executor.execute(GET_V1_LOGS, GET_V1_LOGS.render(), p.toQuery(), null, options, false, LogPage::new);
    }

    /**
     * Asynchronous variant of {@link #list(LogsParams, RequestOptions)}.
     *
     * @param params  filters, page size and cursor
     * @param options per-request overrides, may be {@code null}
     * @return a future of one page
     */
    public CompletableFuture<LogPage> listAsync(LogsParams params, RequestOptions options) {
        return executor.async(() -> list(params, options));
    }

    /**
     * Asynchronous variant of {@link #list(LogsParams)}.
     *
     * @param params filters, page size and cursor
     * @return a future of one page
     */
    public CompletableFuture<LogPage> listAsync(LogsParams params) {
        return listAsync(params, null);
    }

    /**
     * Iterates over every log entry matching the filters, fetching pages lazily by following {@code nextCursor}. Each
     * call to {@code iterator()} starts again from the beginning. The iterator throws
     * {@link StampMitraException} if a page cannot be fetched.
     *
     * @param params filters and page size; a starting cursor is honoured
     * @return an iterable over all entries
     */
    public Iterable<LogEntry> iterate(LogsParams params) {
        return iterate(params, null);
    }

    /**
     * Iterates over every log entry with the server's default filters and page size.
     *
     * @return an iterable over all entries
     */
    public Iterable<LogEntry> iterate() {
        return iterate(LogsParams.none(), null);
    }

    /**
     * Iterates over every log entry matching the filters.
     *
     * @param params  filters and page size; a starting cursor is honoured
     * @param options per-request overrides applied to every page request, may be {@code null}
     * @return an iterable over all entries
     */
    public Iterable<LogEntry> iterate(LogsParams params, RequestOptions options) {
        Objects.requireNonNull(params, "params");
        return () -> new EntryIterator(params, options);
    }

    /**
     * Lazily streams every log entry matching the filters. Close the stream (or use try-with-resources) when you stop
     * early; no background work is done.
     *
     * @param params filters and page size
     * @return a sequential, ordered stream
     */
    public Stream<LogEntry> stream(LogsParams params) {
        Iterator<LogEntry> it = iterate(params).iterator();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it,
                Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    /**
     * Iterates page by page.
     *
     * @param params filters and page size; a starting cursor is honoured
     * @return an iterable over pages
     */
    public Iterable<LogPage> pages(LogsParams params) {
        Objects.requireNonNull(params, "params");
        return () -> new PageIterator(params, null);
    }

    private final class PageIterator implements Iterator<LogPage> {
        private final RequestOptions options;
        private LogsParams next;
        private String lastCursor;

        PageIterator(LogsParams params, RequestOptions options) {
            this.next = params;
            this.options = options;
            this.lastCursor = params.getCursor();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public LogPage next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            LogPage page = list(next, options);
            String cursor = page.getNextCursor();
            if (cursor == null || cursor.isEmpty()) {
                next = null;
            } else if (cursor.equals(lastCursor)) {
                next = null;
                throw new StampMitraException("The API returned the same pagination cursor twice; stopping to avoid "
                        + "an endless loop");
            } else {
                lastCursor = cursor;
                next = next.withCursor(cursor);
            }
            return page;
        }
    }

    private final class EntryIterator implements Iterator<LogEntry> {
        private final PageIterator pages;
        private Iterator<LogEntry> current = Collections.<LogEntry>emptyList().iterator();

        EntryIterator(LogsParams params, RequestOptions options) {
            this.pages = new PageIterator(params, options);
        }

        @Override
        public boolean hasNext() {
            while (!current.hasNext() && pages.hasNext()) {
                current = pages.next().getItems().iterator();
            }
            return current.hasNext();
        }

        @Override
        public LogEntry next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return current.next();
        }
    }
}
