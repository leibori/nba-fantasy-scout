package com.ori_leibo.nbafantasyscout.sync;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.client.SleeperClient;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import com.ori_leibo.nbafantasyscout.persistence.PlayerEntity;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotEntity;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.roster.PlayerMatcher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The daily ingestion: ESPN players and game logs, then Sleeper trending adds/drops.
 * Game logs are fetched in parallel on {@code executor}, one task per player; the HTTP clients rate-limit the calls.
 * Last season's games are fetched once per player (early-season trends use them as the baseline).
 * Deliberately not one transaction: every save commits on its own, so a failure part-way keeps what was stored,
 * and the next run fills the gap (game log upserts are idempotent).
 */
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    /** MDC key put on every log line of one sync, on every thread (see the logging pattern in application.yml). */
    public static final String CORRELATION_ID = "correlationId";

    private final NbaDataClient nba;
    private final SleeperClient sleeper;
    private final PlayerMatcher matcher;
    private final PlayerRepository players;
    private final GameLogUpsert gameLogUpsert;
    private final GameLogRepository gameLogs;
    private final TrendingSnapshotRepository snapshots;
    private final Clock clock;
    private final int season;
    private final int maxConsecutiveFailures;
    private final Executor executor;
    private final ApplicationEventPublisher events;

    public SyncService(NbaDataClient nba, SleeperClient sleeper, PlayerMatcher matcher, PlayerRepository players,
                       GameLogUpsert gameLogUpsert, GameLogRepository gameLogs, TrendingSnapshotRepository snapshots,
                       Clock clock, Executor executor, ApplicationEventPublisher events,
                       int season, int maxConsecutiveFailures) {
        this.nba = nba;
        this.sleeper = sleeper;
        this.matcher = matcher;
        this.players = players;
        this.gameLogUpsert = gameLogUpsert;
        this.gameLogs = gameLogs;
        this.snapshots = snapshots;
        this.clock = clock;
        this.season = season;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.executor = executor;
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

    /** One player's part of the sync; a skipped player (the sync is aborting) counts as neither done nor failed. */
    private record PlayerSync(int gamesChanged, boolean lastSeasonFetched, boolean failed) {
        static final PlayerSync SKIPPED = new PlayerSync(0, false, false);
    }

    private Result runSync() {
        long start = System.nanoTime();
        Instant fetchedAt = clock.instant();
        log.info("Daily sync started for season {}", season);

        List<Player> roster = syncPlayers();
        int lastSeason = season - 1;
        // TODO: players with no games last season (rookies) are asked again every sync, ~80 calls;
        // remember "checked" players if that ever matters
        Set<Long> haveLastSeason = gameLogs.findPlayerIdsWithGamesBetween(
                GameLog.seasonStart(lastSeason), GameLog.seasonEnd(lastSeason));

        // "in a row" across threads: any success resets it, so only a source that keeps failing reaches the limit
        AtomicInteger consecutiveFailures = new AtomicInteger();
        AtomicReference<RuntimeException> abortCause = new AtomicReference<>();
        // submit every player first, then wait; one stream would wait for each task before submitting the next
        List<CompletableFuture<PlayerSync>> tasks = roster.stream()
                .map(player -> CompletableFuture.supplyAsync(
                        () -> syncGames(player, haveLastSeason, consecutiveFailures, abortCause), executor))
                .toList();
        List<PlayerSync> synced = tasks.stream().map(CompletableFuture::join).toList();
        if (abortCause.get() != null) {
            throw new IllegalStateException(
                    "Sync aborted after %d failures in a row; ESPN looks down".formatted(maxConsecutiveFailures),
                    abortCause.get());
        }
        int gamesChanged = synced.stream().mapToInt(PlayerSync::gamesChanged).sum();
        int lastSeasonFetched = (int) synced.stream().filter(PlayerSync::lastSeasonFetched).count();
        int failedPlayers = (int) synced.stream().filter(PlayerSync::failed).count();

        int snapshotCount = syncTrending(roster, fetchedAt);

        Result result = new Result(roster.size(), gamesChanged, lastSeasonFetched, failedPlayers, snapshotCount,
                Duration.ofNanos(System.nanoTime() - start));
        log.info("Daily sync done: {}", result);
        return result;
    }

    // runs on a pool thread
    private PlayerSync syncGames(Player player, Set<Long> haveLastSeason, AtomicInteger consecutiveFailures,
                                 AtomicReference<RuntimeException> abortCause) {
        if (abortCause.get() != null) {
            return PlayerSync.SKIPPED;
        }
        try {
            int changed = gameLogUpsert.upsert(player.id(), nba.fetchGameLogs(player.id(), season));
            boolean backfill = !haveLastSeason.contains(player.id());
            if (backfill) {
                changed += gameLogUpsert.upsert(player.id(), nba.fetchGameLogs(player.id(), season - 1));
            }
            consecutiveFailures.set(0);
            return new PlayerSync(changed, backfill, false);
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            log.error("Game log sync failed for {} ({}), skipping", player.name(), player.id(), e);
            if (consecutiveFailures.incrementAndGet() >= maxConsecutiveFailures) {
                abortCause.compareAndSet(null, e);
            }
            return new PlayerSync(0, false, true);
        }
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

    // crowd data is evidence only, so a Sleeper outage must not fail the sync
    private int syncTrending(List<Player> roster, Instant fetchedAt) {
        try {
            Map<String, String> names = sleeper.fetchPlayerNames();
            List<TrendingSnapshotEntity> rows = new ArrayList<>();
            rows.addAll(toSnapshots(sleeper.fetchTrendingAdds(), TrendingSnapshot.Type.ADD, names, roster, fetchedAt));
            rows.addAll(toSnapshots(sleeper.fetchTrendingDrops(), TrendingSnapshot.Type.DROP, names, roster, fetchedAt));
            snapshots.saveAll(rows);
            return rows.size();
        } catch (RuntimeException e) {
            log.error("Sleeper trending sync failed, continuing without crowd data", e);
            return 0;
        }
    }

    private List<TrendingSnapshotEntity> toSnapshots(Map<String, Integer> counts, TrendingSnapshot.Type type,
                                                     Map<String, String> names, List<Player> roster, Instant fetchedAt) {
        List<TrendingSnapshotEntity> rows = new ArrayList<>();
        counts.forEach((sleeperId, count) -> {
            String name = names.get(sleeperId);
            Optional<PlayerMatcher.Match> match = name == null ? Optional.empty() : matcher.match(name, roster);
            if (match.isEmpty() || match.get().unsure()) {
                log.warn("Skipping Sleeper {} of \"{}\" ({}): no sure ESPN match, best was {}", type, name, sleeperId, match);
                return;
            }
            rows.add(TrendingSnapshotEntity.from(
                    new TrendingSnapshot(match.get().player().id(), type, count, fetchedAt)));
        });
        return rows;
    }
}
