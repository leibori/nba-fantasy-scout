package com.ori_leibo.nbafantasyscout.demo;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.config.ScoutProperties;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.persistence.GameLogEntity;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import com.ori_leibo.nbafantasyscout.persistence.PlayerEntity;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.recommend.TrendService;
import com.ori_leibo.nbafantasyscout.sync.SyncService;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier;
import com.ori_leibo.nbafantasyscout.trend.HotStreakScoring;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import com.ori_leibo.nbafantasyscout.trend.TrendCalculator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Manual end-to-end check against the real ESPN and Sleeper APIs and the dev Postgres:
 * the wired SyncService fills the DB, then the data flows into TrendCalculator.
 * Runs only with the "demo" profile; see CLAUDE.md "Local setup" for the command.
 * Prints to the console on purpose: it is a report for a human, not application logging.
 */
@Component
@Profile("demo")
class DemoRunner implements CommandLineRunner {

    private static final String SAMPLE_PLAYER = "Luka Doncic";

    private final SyncService sync;
    private final NbaDataClient espn;
    private final PlayerRepository players;
    private final GameLogRepository gameLogs;
    private final GameLogUpsert upsert;
    private final TrendingSnapshotRepository snapshots;
    private final TrendCalculator trendCalculator;
    private final HotStreakScoring hotStreakScoring;
    private final TrendService trendService;
    private final int season;
    private int failures;

    DemoRunner(SyncService sync, NbaDataClient espn, PlayerRepository players, GameLogRepository gameLogs,
               GameLogUpsert upsert, TrendingSnapshotRepository snapshots, TrendCalculator trendCalculator,
               HotStreakScoring hotStreakScoring, TrendService trendService, ScoutProperties properties) {
        this.sync = sync;
        this.espn = espn;
        this.players = players;
        this.gameLogs = gameLogs;
        this.upsert = upsert;
        this.snapshots = snapshots;
        this.trendCalculator = trendCalculator;
        this.hotStreakScoring = hotStreakScoring;
        this.trendService = trendService;
        this.season = properties.sync().season();
    }

    @Override
    public void run(String... args) {
        System.out.println("\n=== 1. Daily sync: ESPN players + game logs, Sleeper trending -> DB");
        SyncService.Result result = sync.syncDaily();
        System.out.printf("  %d players, %d game logs new or changed, last season fetched for %d, %d failed players,"
                        + " %d snapshots, took %d s%n",
                result.players(), result.gamesChanged(), result.lastSeasonFetched(), result.failedPlayers(), result.snapshots(),
                result.took().toSeconds());
        List<Player> stored = players.findAll().stream().map(PlayerEntity::toPlayer).toList();
        check("ESPN returned 30 teams' worth of players (" + result.players() + ")", result.players() > 400);
        check("players table holds them (" + stored.size() + ")", stored.size() >= result.players());
        check("some players have an injury status", stored.stream().anyMatch(p -> p.status() != null));
        check("no player's game logs failed", result.failedPlayers() == 0);
        // more than one game per player on average: true from about the second game of a season
        check("game_logs holds games for the roster (" + gameLogs.count() + ")", gameLogs.count() > result.players());
        check("Sleeper trending stored as snapshots (" + result.snapshots() + ", " + snapshots.count() + " in total)",
                result.snapshots() > 0);

        Optional<Player> sample = stored.stream().filter(p -> p.name().equals(SAMPLE_PLAYER)).findFirst();
        if (sample.isEmpty()) {
            System.out.println("  [SKIP] " + SAMPLE_PLAYER + " is not stored; skipping sections 2 and 3");
        } else {
            Player player = sample.get();
            System.out.println("\n=== 2. Upsert " + player.name() + "'s season again");
            int changed = upsert.upsert(player.id(), espn.fetchGameLogs(player.id(), season));
            check(player.name() + ": second upsert changed nothing (ON CONFLICT)", changed == 0);

            System.out.println("\n=== 3. Read back from the DB -> TrendCalculator -> HotStreakScoring");
            List<GameLog> games = gameLogs.findByPlayerIdOrderByGameDateAsc(player.id()).stream()
                    .map(GameLogEntity::toGameLog)
                    .toList();
            List<GameLog> thisSeason = games.stream().filter(g -> g.season() == season).toList();
            List<GameLog> lastSeason = games.stream().filter(g -> g.season() == season - 1).toList();
            Optional<Trend> trend = trendCalculator.calculate(thisSeason, lastSeason);
            check(player.name() + ": trend computed from " + thisSeason.size() + " games this season, "
                    + lastSeason.size() + " last season", trend.isPresent());
            trend.ifPresent(t -> System.out.printf(
                    "  recent %.1f pts / %.1f min, baseline (%s%s) %.1f pts / %.1f min, hot score %.3f%n",
                    t.recent().points(), t.recent().minutes(), t.baselineSource(), t.provisional() ? ", provisional" : "",
                    t.baseline().points(), t.baseline().minutes(), hotStreakScoring.score(t)));
        }

        System.out.println("\n=== 4. Form of every rostered player (TrendService)");
        List<TrendService.PlayerForm> forms = trendService.formOfAllPlayers();
        Map<FormClassifier.Form, Long> byForm = forms.stream()
                .collect(Collectors.groupingBy(TrendService.PlayerForm::form, TreeMap::new, Collectors.counting()));
        System.out.println("  " + byForm);
        List<TrendService.PlayerForm> byValue = forms.stream()
                .filter(f -> f.value() != null)
                .sorted(Comparator.comparing(TrendService.PlayerForm::value).reversed())
                .toList();
        System.out.println("  top 10 by value:");
        byValue.stream().limit(10).forEach(DemoRunner::printForm);
        System.out.println("  early hot, by value:");
        byValue.stream().filter(f -> f.form() == FormClassifier.Form.EARLY_HOT).forEach(DemoRunner::printForm);
        System.out.println("  returning from a lost season:");
        byValue.stream().filter(f -> f.form() == FormClassifier.Form.RETURNING).forEach(DemoRunner::printForm);
        if (byValue.size() >= 150) {
            System.out.printf("  value of the 50th / 100th / 150th player: %.2f / %.2f / %.2f%n",
                    byValue.get(49).value(), byValue.get(99).value(), byValue.get(149).value());
        }
        check("every rostered player has a form (" + forms.size() + ")",
                forms.size() == stored.stream().filter(p -> p.team() != null).count());
        check("players with games this season have a value (" + byValue.size() + ")", byValue.size() > 300);

        System.out.println(failures == 0 ? "\nDEMO PASSED\n" : "\nDEMO FAILED: " + failures + " check(s)\n");
    }

    private static void printForm(TrendService.PlayerForm form) {
        System.out.printf("    %-26s %-4s %2d games  value %6.2f  hot %s  %s%n", form.player().name(), form.player().team(),
                form.gamesThisSeason(), form.value(),
                form.hotScore() == null ? "  -   " : "%+.3f".formatted(form.hotScore()), form.form());
    }

    private void check(String what, boolean ok) {
        if (!ok) {
            failures++;
        }
        System.out.println("  [" + (ok ? "OK" : "FAIL") + "] " + what);
    }
}
