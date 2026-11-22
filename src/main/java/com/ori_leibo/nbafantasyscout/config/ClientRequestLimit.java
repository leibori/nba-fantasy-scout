package com.ori_leibo.nbafantasyscout.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most {@code max} requests per client in each fixed window. A fixed window lets a client spend up to twice the
 * limit across a window edge; that is fine for keeping one visitor from crowding out the others.
 */
public class ClientRequestLimit {

    // above this many tracked clients, finished windows are dropped, so a flood of addresses can't grow the map forever
    private static final int PRUNE_ABOVE = 10_000;

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public ClientRequestLimit(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    private record Window(Instant start, int count) {
    }

    /** {@code retryAfterSeconds}: when the client may try again, only when not allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    public Decision tryAcquire(String client) {
        Instant now = clock.instant();
        // compute is atomic per key, so two requests from one client at once can't both take the last slot
        Window current = windows.compute(client, (key, old) -> old == null || ended(old, now)
                ? new Window(now, 1)
                : new Window(old.start(), old.count() + 1));
        if (windows.size() > PRUNE_ABOVE) {
            windows.values().removeIf(old -> ended(old, now));
        }
        if (current.count() <= max) {
            return new Decision(true, 0);
        }
        long millisLeft = Duration.between(now, current.start().plus(window)).toMillis();
        return new Decision(false, Math.max(1, (millisLeft + 999) / 1000));
    }

    private boolean ended(Window window, Instant now) {
        return !now.isBefore(window.start().plus(this.window));
    }
}
