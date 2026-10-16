package in.stampmitra.sdk;

import java.time.Duration;

/** Pauses between retry attempts. Replace it in tests to avoid real waiting. */
@FunctionalInterface
public interface Sleeper {

    /**
     * Blocks for the given time.
     *
     * @param duration how long to wait
     * @throws InterruptedException if the waiting thread is interrupted
     */
    void sleep(Duration duration) throws InterruptedException;
}
