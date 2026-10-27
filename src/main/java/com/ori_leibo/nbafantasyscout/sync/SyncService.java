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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The daily ingestion: ESPN players and game logs, then Sleeper trending adds/drops.
 * Last season's games are fetched once per player (early-season trends use them as the baseline).
 * Deliberately not one transaction: every save commits on its own, so a failure part-way keeps what was stored,
 * and the next run fills the gap (game log upserts are idempotent).
 */
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

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

    public SyncService(NbaDataClient nba, SleeperClient sleeper, PlayerMatcher matcher, PlayerRepository players,
                       GameLogUpsert gameLogUpsert, GameLogRepository gameLogs, TrendingSnapshotRepository snapshots,
                       Clock clock,
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
    }

    /** What one sync did; {@code took} is the wall time, the number to compare once fetching runs in parallel. */
    public record Result(int players, int gamesChanged, int lastSeasonFetched, int failedPlayers, int snapshots,
                         Duration took) {
    }

    /** Throws only when the sync cannot go on: no players from ESPN, or ESPN failing call after call. */
    public Result syncDaily() {
        long start = System.nanoTime();
        Instant fetchedAt = clock.instant();
        log.info("Daily sync started for season {}", season);

        List<Player> roster = syncPlayers();
        int lastSeason = season - 1;
        // shortcut: players with no games last season (rookies) are asked again every sync, ~80 calls;
        // remember "checked" players if that ever matters
        Set<Long> haveLastSeason = gameLogs.findPlayerIdsWithGamesBetween(
                GameLog.seasonStart(lastSeason), GameLog.seasonEnd(lastSeason));

        int gamesChanged = 0;
        int lastSeasonFetched = 0;
        int failedPlayers = 0;
        int consecutiveFailures = 0;
        for (Player player : roster) {
            try {
                gamesChanged += gameLogUpsert.upsert(player.id(), nba.fetchGameLogs(player.id(), season));
                if (!haveLastSeason.contains(player.id())) {
                    gamesChanged += gameLogUpsert.upsert(player.id(), nba.fetchGameLogs(player.id(), lastSeason));
                    lastSeasonFetched++;
                }
                consecutiveFailures = 0;
            } catch (RuntimeException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                failedPlayers++;
                log.error("Game log sync failed for {} ({}), skipping", player.name(), player.id(), e);
                if (++consecutiveFailures >= maxConsecutiveFailures) {
                    throw new IllegalStateException(
                            "Sync aborted after %d failures in a row; ESPN looks down".formatted(consecutiveFailures), e);
                }
            }
        }

        int snapshotCount = syncTrending(roster, fetchedAt);

        Result result = new Result(roster.size(), gamesChanged, lastSeasonFetched, failedPlayers, snapshotCount,
                Duration.ofNanos(System.nanoTime() - start));
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
