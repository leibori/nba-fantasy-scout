package com.ori_leibo.nbafantasyscout.sync;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.persistence.PlayerEntity;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The daily ingestion: ESPN players, then their game logs ({@link GameLogSync}), then Sleeper trending adds/drops
 * ({@link TrendingSync}).
 * Deliberately not one transaction: every save commits on its own, so a failure part-way keeps what was stored,
 * and the next run fills the gap (game log upserts are idempotent).
 */
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    /** MDC key put on every log line of one sync, on every thread (see the logging pattern in application.yml). */
    public static final String CORRELATION_ID = "correlationId";

    private final NbaDataClient nba;
    private final PlayerRepository players;
    private final GameLogSync gameLogSync;
    private final TrendingSync trendingSync;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public SyncService(NbaDataClient nba, PlayerRepository players, GameLogSync gameLogSync, TrendingSync trendingSync,
                       Clock clock, ApplicationEventPublisher events) {
        this.nba = nba;
        this.players = players;
        this.gameLogSync = gameLogSync;
        this.trendingSync = trendingSync;
        this.clock = clock;
        this.events = events;
    }

    /** What one sync did; {@code took} is the wall time, the number to compare once fetching runs in parallel. */
    public record Result(int players, int gamesChanged, int lastSeasonFetched, int failedPlayers, int snapshots,
                         Duration took) {
    }

    /**
     * Throws only when the sync cannot go on: no players from ESPN, or ESPN failing call after call.
     * A {@link SyncCompletedEvent} is published either way: an aborted sync has still stored new data.
     */
    public Result syncDaily() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CORRELATION_ID, "sync-" + UUID.randomUUID().toString().substring(0, 8))) {
            Result result = null;

            try {
                result = runSync();
                return result;
            } finally {
                events.publishEvent(new SyncCompletedEvent(result));
            }
        }
    }

    private Result runSync() {

        long start = System.nanoTime();
        Instant fetchedAt = clock.instant();
        log.info("Daily sync started");

        List<Player> roster = syncPlayers();
        GameLogSync.Result games = gameLogSync.sync(roster);
        int snapshotCount = trendingSync.sync(roster, fetchedAt);

        Result result = new Result(roster.size(), games.gamesChanged(), games.lastSeasonFetched(), games.failedPlayers(),
                snapshotCount, Duration.ofNanos(System.nanoTime() - start));
        log.info("Daily sync done: {}", result);
        return result;
    }

    private List<Player> syncPlayers() {
        List<Player> roster = nba.fetchPlayers();
        // an empty list would mark every stored player as off-roster
        if (roster.isEmpty()) {
            throw new IllegalStateException("ESPN returned no players; keeping the stored ones");
        }
        players.saveAll(roster.stream().map(PlayerEntity::from).toList());
        int offRoster = players.clearTeamExcept(roster.stream().map(Player::id).toList());
        if (offRoster > 0) {
            log.info("{} stored players are no longer on an NBA roster", offRoster);
        }
        return roster;
    }
}
