package in.stampmitra.sdk.internal;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/** Runs blocking SDK calls on an executor and wires {@code cancel(true)} to thread interruption. Internal. */
public final class Async {

    private Async() {
    }

    /**
     * Runs a call asynchronously.
     *
     * @param executor where to run the blocking call
     * @param call     the call
     * @param <T>      result type
     * @return a future completed with the result or the exception thrown by the call
     */
    public static <T> CompletableFuture<T> run(Executor executor, Callable<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        AtomicReference<Thread> runner = new AtomicReference<>();
        future.whenComplete((value, error) -> {
            if (future.isCancelled()) {
                Thread t = runner.get();
                if (t != null) {
                    t.interrupt();
                }
            }
        });
        executor.execute(() -> {
            if (future.isDone()) {
                return;
            }
            runner.set(Thread.currentThread());
            try {
                if (!future.isDone()) {
                    future.complete(call.call());
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
            } finally {
                runner.set(null);
                if (future.isCancelled()) {
                    Thread.interrupted(); // clear a late interrupt before the pooled thread is reused
                }
            }
        });
        return future;
    }
}
