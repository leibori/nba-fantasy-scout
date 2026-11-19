package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.persistence.GameLogEntity;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.PlayerEntity;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotEntity;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier;
import com.ori_leibo.nbafantasyscout.trend.ScoringStrategy;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import com.ori_leibo.nbafantasyscout.trend.TrendCalculator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;

/**
 * The part of every player's form that is the same in every league format: stored players and games,
 * season averages, trend, hot score and the latest Sleeper snapshot. Loading it is the expensive part (~25k rows),
 * so it is cached until the next sync; value and form are cheap and computed per format by {@link TrendService}.
 */
public class SeasonData {

    private static final Logger log = LoggerFactory.getLogger(SeasonData.class);
    public static final String CACHE = "season";

    private final PlayerRepository players;
    private final GameLogRepository gameLogs;
    private final TrendingSnapshotRepository snapshots;
    private final TrendCalculator calculator;
    private final ScoringStrategy hotScoring;
    private final Clock clock;
    private final double leagueMinMinutes;

    public SeasonData(PlayerRepository players, GameLogRepository gameLogs, TrendingSnapshotRepository snapshots,
                      TrendCalculator calculator, ScoringStrategy hotScoring, Clock clock, double leagueMinMinutes) {
        this.players = players;
        this.gameLogs = gameLogs;
        this.snapshots = snapshots;
        this.calculator = calculator;
        this.hotScoring = hotScoring;
        this.clock = clock;
        this.leagueMinMinutes = leagueMinMinutes;
    }

    /**
     * One rostered player. {@code trend} and {@code hotScore} are null when there is no trend yet; {@code line}
     * (this season's per-game averages) is null when he has no games this season; {@code crowd} is his row in the
     * latest Sleeper trending lists, null when he is in neither.
     */
    public record PlayerSeason(Player player, int gamesThisSeason, Trend trend, Double hotScore, StatLine line,
                               FormClassifier.History history, TrendingSnapshot crowd) {
    }

    /** {@code league}: the per-game lines values are measured against (players with enough minutes). */
    public record Snapshot(List<PlayerSeason> players, List<StatLine> league) {

        // shared by every request through the cache, so nobody may change it
        public Snapshot {
            players = List.copyOf(players);
            league = List.copyOf(league);
        }
    }

    /** Every player on a roster, in no particular order. */
    // sync: requests that arrive while the cache is empty wait for one load instead of each loading the DB
    @Cacheable(cacheNames = CACHE, sync = true)
    public Snapshot load() {
        long start = System.nanoTime();
        int season = GameLog.seasonOf(LocalDate.now(clock));
        List<Player> roster = players.findAll().stream()
                .map(PlayerEntity::toPlayer)
                .filter(player -> player.team() != null)
                .toList();
        // one query for all games (~25k small rows) instead of one per player
        Map<Long, List<GameLog>> gamesByPlayer = gameLogs.findAll().stream()
                .collect(Collectors.groupingBy(GameLogEntity::playerId,
                        Collectors.mapping(GameLogEntity::toGameLog, Collectors.toList())));
        Map<Long, TrendingSnapshot> crowdByPlayer = latestCrowd();

        List<PlayerSeason> seasons = new ArrayList<>();
        for (Player player : roster) {
            List<GameLog> games = gamesByPlayer.getOrDefault(player.id(), List.of());
            List<GameLog> current = games.stream().filter(game -> game.season() == season).toList();
            List<GameLog> last = games.stream().filter(game -> game.season() == season - 1).toList();
            Trend trend = calculator.calculate(current, last).orElse(null);
            seasons.add(new PlayerSeason(player, current.size(), trend,
                    trend == null ? null : hotScoring.score(trend),
                    current.isEmpty() ? null : StatLine.average(current),
                    history(player, last), crowdByPlayer.get(player.id())));
        }
        List<StatLine> league = seasons.stream()
                .map(PlayerSeason::line)
                .filter(line -> line != null && line.minutes() >= leagueMinMinutes)
                .toList();
        log.info("Loaded the season of {} players, a value league of {}, in {} ms",
                seasons.size(), league.size(), (System.nanoTime() - start) / 1_000_000);
        return new Snapshot(seasons, league);
    }

    // a player in both lists (rare) is judged by the bigger count
    private Map<Long, TrendingSnapshot> latestCrowd() {
        return snapshots.findFirstByOrderByFetchedAtDesc()
                .map(latest -> snapshots.findByFetchedAt(latest.toSnapshot().fetchedAt()).stream()
                        .map(TrendingSnapshotEntity::toSnapshot)
                        .collect(Collectors.toMap(TrendingSnapshot::playerId, snapshot -> snapshot,
                                (a, b) -> a.count() >= b.count() ? a : b)))
                .orElse(Map.of());
    }

    // no games last season: a rookie by ESPN's experience count (0), otherwise back from a lost season
    private static FormClassifier.History history(Player player, List<GameLog> lastSeason) {
        if (!lastSeason.isEmpty()) {
            return FormClassifier.History.PLAYED_LAST_SEASON;
        }
        return Integer.valueOf(0).equals(player.experienceYears())
                ? FormClassifier.History.ROOKIE
                : FormClassifier.History.RETURNING;
    }
}
