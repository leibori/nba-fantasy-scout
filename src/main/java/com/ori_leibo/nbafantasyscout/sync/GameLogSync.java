package com.ori_leibo.nbafantasyscout.sync;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches and upserts the game logs of every rostered player, in parallel on {@code executor}, one task per player;
 * the HTTP client rate-limits the calls. Last season's games are fetched once per player (early-season trends use
 * them as the baseline). A failing player is skipped; failures in a row abort the whole sync.
 */
public class GameLogSync {

    private static final Logger log = LoggerFactory.getLogger(GameLogSync.class);

    private final NbaDataClient nba;
    private final GameLogUpsert gameLogUpsert;
    private final GameLogRepository gameLogs;
    private final Executor executor;
    private final Clock clock;
    private final int maxConsecutiveFailures;

    public GameLogSync(NbaDataClient nba, GameLogUpsert gameLogUpsert, GameLogRepository gameLogs, Executor executor,
                       Clock clock, int maxConsecutiveFailures) {
        this.nba = nba;
        this.gameLogUpsert = gameLogUpsert;
        this.gameLogs = gameLogs;
        this.executor = executor;
        this.clock = clock;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public record Result(int gamesChanged, int lastSeasonFetched, int failedPlayers) {
    }

    /** One player's part of the sync; a skipped player (the sync is aborting) counts as neither done nor failed. */
    private record PlayerSync(int gamesChanged, boolean lastSeasonFetched, boolean failed) {
        static final PlayerSync SKIPPED = new PlayerSync(0, false, false);
    }

    /** Throws when ESPN fails call after call: it looks down, so going on would only skip every player. */
    public Result sync(List<Player> roster) {
        // read per sync, not at startup: a server running past August 1 moves on to the new season by itself
        int season = GameLog.seasonOf(LocalDate.now(clock));
        log.info("Syncing game logs of {} players for season {}", roster.size(), season);
        int lastSeason = season - 1;
        // TODO: veterans with no games last season (a lost season) are asked again every sync; only a handful,
        // remember "checked" players if that ever matters
        Set<Long> haveLastSeason = gameLogs.findPlayerIdsWithGamesBetween(
                GameLog.seasonStart(lastSeason), GameLog.seasonEnd(lastSeason));

        // "in a row" across threads: any success resets it, so only a source that keeps failing reaches the limit
        AtomicInteger consecutiveFailures = new AtomicInteger();
        AtomicReference<RuntimeException> abortCause = new AtomicReference<>();
        // submit every player first, then wait; one stream would wait for each task before submitting the next
        List<CompletableFuture<PlayerSync>> tasks = roster.stream()
                .map(player -> CompletableFuture.supplyAsync(
                        () -> syncPlayer(player, season, haveLastSeason, consecutiveFailures, abortCause), executor))
                .toList();
        List<PlayerSync> synced = tasks.stream().map(CompletableFuture::join).toList();
        if (abortCause.get() != null) {
            throw new IllegalStateException(
                    "Sync aborted after %d failures in a row; ESPN looks down".formatted(maxConsecutiveFailures),
                    abortCause.get());
        }
        return new Result(
                synced.stream().mapToInt(PlayerSync::gamesChanged).sum(),
                (int) synced.stream().filter(PlayerSync::lastSeasonFetched).count(),
                (int) synced.stream().filter(PlayerSync::failed).count());
    }

    // runs on a pool thread
    private PlayerSync syncPlayer(Player player, int season, Set<Long> haveLastSeason, AtomicInteger consecutiveFailures,
                                  AtomicReference<RuntimeException> abortCause) {
        if (abortCause.get() != null) {
            return PlayerSync.SKIPPED;
        }
        try {
            int changed = gameLogUpsert.upsert(player.id(), nba.fetchGameLogs(player.id(), season));
            // a rookie has no last season to fetch; unknown experience (null) still fetches
            boolean backfill = !haveLastSeason.contains(player.id())
                    && !Integer.valueOf(0).equals(player.experienceYears());
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
}
