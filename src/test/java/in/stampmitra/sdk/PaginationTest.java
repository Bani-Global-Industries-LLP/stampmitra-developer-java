package in.stampmitra.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.stampmitra.sdk.errors.StampMitraException;
import in.stampmitra.sdk.errors.UpstreamException;
import in.stampmitra.sdk.models.LogEntry;
import in.stampmitra.sdk.models.LogPage;
import in.stampmitra.sdk.resources.LogsParams;
import in.stampmitra.sdk.support.FakeTransport;
import in.stampmitra.sdk.support.TestClients;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaginationTest {

    private static String page(String id, String next) {
        return "{\"items\":[{\"id\":\"" + id + "\",\"createdAt\":\"2026-10-15T09:41:07.000Z\",\"service\":\"pan.verify\"}],"
                + "\"nextCursor\":" + (next == null ? "null" : "\"" + next + "\"") + "}";
    }

    private static FakeTransport threePages() {
        return new FakeTransport().thenOk(page("a", "c1")).thenOk(page("b", "c2")).thenOk(page("c", null));
    }

    @Test
    void iterateFollowsNextCursorAcrossThreePages() {
        FakeTransport t = threePages();
        List<String> ids = new ArrayList<>();
        for (LogEntry e : TestClients.client(t).logs().iterate(LogsParams.builder().limit(1).build())) {
            ids.add(e.getId());
        }
        assertThat(ids).containsExactly("a", "b", "c");
        assertThat(t.requests()).hasSize(3);
        assertThat(t.requests().get(0).getUri().getRawQuery()).isEqualTo("limit=1");
        assertThat(t.requests().get(1).getUri().getRawQuery()).isEqualTo("limit=1&cursor=c1");
        assertThat(t.requests().get(2).getUri().getRawQuery()).isEqualTo("limit=1&cursor=c2");
    }

    @Test
    void streamIsLazy() {
        FakeTransport t = threePages();
        List<String> first = TestClients.client(t).logs().stream(LogsParams.none()).limit(2).map(LogEntry::getId)
                .collect(Collectors.toList());
        assertThat(first).containsExactly("a", "b");
        assertThat(t.requests()).hasSize(2); // the third page is never fetched
    }

    @Test
    void iterationIsLazyUntilNext() {
        FakeTransport t = threePages();
        Iterator<LogEntry> it = TestClients.client(t).logs().iterate().iterator();
        assertThat(t.requests()).isEmpty();
        assertThat(it.hasNext()).isTrue();
        assertThat(t.requests()).hasSize(1);
        assertThat(it.next().getId()).isEqualTo("a");
    }

    @Test
    void eachIteratorRestartsFromTheBeginning() {
        FakeTransport t = threePages();
        Iterable<LogEntry> all = TestClients.client(t).logs().iterate();
        int count = 0;
        for (LogEntry ignored : all) {
            count++;
        }
        assertThat(count).isEqualTo(3);
        assertThat(all.iterator().hasNext()).isTrue();
    }

    @Test
    void aStartingCursorIsHonoured() {
        FakeTransport t = new FakeTransport().thenOk(page("z", null));
        TestClients.client(t).logs().iterate(LogsParams.builder().cursor("start").build()).iterator().next();
        assertThat(t.last().getUri().getRawQuery()).isEqualTo("cursor=start");
    }

    @Test
    void emptyPagesWithACursorKeepGoingAndEmptyCursorsStop() {
        FakeTransport t = new FakeTransport().thenOk("{\"items\":[],\"nextCursor\":\"c1\"}").thenOk(page("a", ""));
        assertThat(TestClients.client(t).logs().stream(LogsParams.none()).count()).isEqualTo(1);
        assertThat(t.requests()).hasSize(2);
    }

    @Test
    void aCursorThatDoesNotAdvanceIsAnError() {
        FakeTransport t = new FakeTransport().thenOk(page("a", "same")).thenOk(page("b", "same"));
        assertThatThrownBy(() -> TestClients.client(t).logs().stream(LogsParams.none()).count())
                .isInstanceOf(StampMitraException.class).hasMessageContaining("same pagination cursor");
    }

    @Test
    void pagesIterateWholePages() {
        List<String> cursors = new ArrayList<>();
        for (LogPage p : TestClients.client(threePages()).logs().pages(LogsParams.none())) {
            cursors.add(p.getNextCursor());
        }
        assertThat(cursors).containsExactly("c1", "c2", null);
    }

    @Test
    void errorsSurfaceFromTheIterator() {
        FakeTransport t = new FakeTransport().thenOk(page("a", "c1")).then(503, "{}");
        StampMitra c = TestClients.builder(t, TestClients.sleeps()).maxRetries(0).build();
        Iterator<LogEntry> it = c.logs().iterate().iterator();
        assertThat(it.next().getId()).isEqualTo("a");
        assertThatThrownBy(it::hasNext).isInstanceOf(UpstreamException.class);
    }

    @Test
    void exhaustedIteratorsThrow() {
        Iterator<LogEntry> it = TestClients.client(new FakeTransport().thenOk(page("a", null))).logs().iterate().iterator();
        it.next();
        assertThat(it.hasNext()).isFalse();
        assertThatThrownBy(it::next).isInstanceOf(java.util.NoSuchElementException.class);
    }
}
