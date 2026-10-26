package com.ori_leibo.nbafantasyscout.demo;

import com.ori_leibo.nbafantasyscout.client.EspnGameLogMapper;
import com.ori_leibo.nbafantasyscout.client.EspnNbaDataClient;
import com.ori_leibo.nbafantasyscout.client.EspnRosterMapper;
import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.client.SleeperClient;
import com.ori_leibo.nbafantasyscout.client.SleeperMapper;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.persistence.GameLogEntity;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import com.ori_leibo.nbafantasyscout.persistence.PlayerEntity;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.roster.PlayerMatcher;
import com.ori_leibo.nbafantasyscout.trend.HotStreakScoring;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import com.ori_leibo.nbafantasyscout.trend.TrendCalculator;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Manual end-to-end check against the real ESPN API and the dev Postgres: ESPN -> DB -> TrendCalculator.
 * Runs only with the "demo" profile; see CLAUDE.md "Local setup" for the command.
 * Prints to the console on purpose: it is a report for a human, not application logging.
 */
@Component
@Profile("demo")
class DemoRunner implements CommandLineRunner {

    private static final int SEASON = 2026;
    private static final List<String> SAMPLE_PLAYERS = List.of("Luka Doncic", "Nikola Jokic", "Shai Gilgeous-Alexander");

    private final PlayerRepository players;
    private final GameLogRepository gameLogs;
    private final GameLogUpsert upsert;
    private int failures;

    DemoRunner(PlayerRepository players, GameLogRepository gameLogs, GameLogUpsert upsert) {
        this.players = players;
        this.gameLogs = gameLogs;
        this.upsert = upsert;
    }

    @Override
    public void run(String... args) {
        // wired by hand until SyncService and its config exist (step 5.6)
        NbaDataClient espn = new EspnNbaDataClient(HttpClient.newHttpClient(),
                "https://site.api.espn.com/apis/site/v2/sports/basketball/nba",
                "https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba",
                Duration.ofSeconds(10), new EspnRosterMapper(), new EspnGameLogMapper());

        System.out.println("\n=== 1. Players: ESPN rosters -> players table");
        List<Player> roster = espn.fetchPlayers();
        players.saveAll(roster.stream().map(PlayerEntity::from).toList());
        check("ESPN returned 30 teams' worth of players (" + roster.size() + ")", roster.size() > 400);
        check("players table holds them (" + players.count() + ")", players.count() >= roster.size());
        check("some players have an injury status", roster.stream().anyMatch(p -> p.status() != null));

        System.out.println("\n=== 2. Game logs " + SEASON + ": ESPN -> game_logs table, inserted twice");
        for (String name : SAMPLE_PLAYERS) {
            Optional<Player> player = roster.stream().filter(p -> p.name().equals(name)).findFirst();
            if (player.isEmpty()) {
                System.out.println("  [SKIP] " + name + " is not on a current roster");
                continue;
            }
            List<GameLog> season = espn.fetchGameLogs(player.get().id(), SEASON);
            upsert.upsert(player.get().id(), season);
            int secondRun = upsert.upsert(player.get().id(), season);
            int stored = gameLogs.findByPlayerIdOrderByGameDateAsc(player.get().id()).size();
            check(name + ": fetched " + season.size() + " games, " + stored + " stored", season.size() > 0 && stored == season.size());
            check(name + ": second upsert changed nothing (ON CONFLICT)", secondRun == 0);
        }

        System.out.println("\n=== 3. Read back from the DB -> TrendCalculator -> HotStreakScoring");
        roster.stream().filter(p -> p.name().equals(SAMPLE_PLAYERS.getFirst())).findFirst().ifPresent(player -> {
            List<GameLog> stored = gameLogs.findByPlayerIdOrderByGameDateAsc(player.id()).stream()
                    .map(GameLogEntity::toGameLog)
                    .toList();
            Optional<Trend> trend = new TrendCalculator(3, 5, 15).calculate(stored);
            check(player.name() + ": trend computed from " + stored.size() + " stored games", trend.isPresent());
            trend.ifPresent(t -> System.out.printf(
                    "  recent %.1f pts / %.1f min, baseline %.1f pts / %.1f min, hot score %.3f%n",
                    t.recent().points(), t.recent().minutes(), t.baseline().points(), t.baseline().minutes(),
                    new HotStreakScoring(0.4, 0.3, 0.3).score(t)));
        });

        System.out.println("\n=== 4. Sleeper trending adds -> PlayerMatcher -> ESPN players");
        SleeperClient sleeper = new SleeperClient(HttpClient.newHttpClient(), "https://api.sleeper.app/v1",
                Duration.ofSeconds(30), 24, 25, new SleeperMapper());
        Map<String, Integer> adds = sleeper.fetchTrendingAdds();
        Map<String, String> sleeperNames = sleeper.fetchPlayerNames();
        PlayerMatcher matcher = new PlayerMatcher(0.85);
        int sure = 0;
        for (Map.Entry<String, Integer> add : adds.entrySet()) {
            String name = sleeperNames.getOrDefault(add.getKey(), "?");
            Optional<PlayerMatcher.Match> match = matcher.match(name, roster);
            if (match.isPresent() && !match.get().unsure()) {
                sure++;
            }
            System.out.printf("  %-26s %6d adds -> %s%n", name, add.getValue(), match
                    .map(m -> "%s (%.2f%s)".formatted(m.player().name(), m.confidence(), m.unsure() ? ", unsure" : ""))
                    .orElse("no match"));
        }
        check("Sleeper returned trending adds (" + adds.size() + ")", !adds.isEmpty());
        check("Sleeper player names loaded (" + sleeperNames.size() + ")", sleeperNames.size() > 400);
        check("most trending names matched an ESPN player (" + sure + "/" + adds.size() + ")", sure * 10 >= adds.size() * 9);

        System.out.println(failures == 0 ? "\nDEMO PASSED\n" : "\nDEMO FAILED: " + failures + " check(s)\n");
    }

    private void check(String what, boolean ok) {
        if (!ok) {
            failures++;
        }
        System.out.println("  [" + (ok ? "OK" : "FAIL") + "] " + what);
    }
}
