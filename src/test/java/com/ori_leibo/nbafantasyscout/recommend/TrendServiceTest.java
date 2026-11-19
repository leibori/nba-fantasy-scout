package com.ori_leibo.nbafantasyscout.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ori_leibo.nbafantasyscout.TestcontainersConfiguration;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import com.ori_leibo.nbafantasyscout.persistence.PlayerEntity;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotEntity;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import com.ori_leibo.nbafantasyscout.trend.HotStreakScoring;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import com.ori_leibo.nbafantasyscout.trend.TrendCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

// real Postgres: the service's job is loading and splitting stored games, so the repositories are not mocked
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, GameLogUpsert.class})
class TrendServiceTest {

    // a day in the 2026-27 season
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private static final LeagueScoring CATEGORIES = new LeagueScoring.Categories();
    private static final LeagueScoring POINTS = new LeagueScoring.Points(Map.of(
            PointStat.PTS, 1.0, PointStat.REB, 1.2, PointStat.AST, 1.5, PointStat.STL, 3.0, PointStat.BLK, 3.0,
            PointStat.TO, -1.0));

    @Autowired
    private PlayerRepository players;
    @Autowired
    private GameLogRepository gameLogs;
    @Autowired
    private GameLogUpsert upsert;
    @Autowired
    private TrendingSnapshotRepository snapshots;

    @Test
    void judgesRookiesAndVeteransEarlyInTheSeasonAndSkipsOffRosterPlayers() {
        players.saveAllAndFlush(List.of(
                player(1, "Big Rookie", "DUK", 0), player(2, "Steady Veteran", "LAL", 6),
                player(3, "Bench Guard", "BOS", 3), player(4, "Waived Player", null, 2),
                player(5, "Back From Injury", "IND", 7)));
        upsert.upsert(1, games(2026, 10, 3, 34, 24));                                  // rookie, 3 big games
        upsert.upsert(2, games(2026, 10, 3, 30, 15));                                  // veteran, as last season
        upsert.upsert(2, games(2026, 1, 10, 30, 15));
        upsert.upsert(3, games(2026, 10, 3, 8, 3));                                    // bench, 8 minutes
        upsert.upsert(3, games(2026, 1, 10, 8, 3));
        upsert.upsert(4, games(2026, 10, 3, 34, 30));
        upsert.upsert(5, games(2026, 10, 3, 34, 26));                                  // missed all of last season
        // the day before, the crowd dropped the rookie; the latest sync has him as a top add and the veteran as a drop
        snapshots.saveAllAndFlush(List.of(
                snapshot(1, TrendingSnapshot.Type.DROP, 50, "2026-10-26T06:00:00Z"),
                snapshot(1, TrendingSnapshot.Type.ADD, 9000, "2026-10-27T06:00:00Z"),
                snapshot(2, TrendingSnapshot.Type.DROP, 400, "2026-10-27T06:00:00Z")));

        Map<String, PlayerForm> forms = service().formOfAllPlayers(CATEGORIES).stream()
                .collect(Collectors.toMap(form -> form.player().name(), form -> form));

        assertEquals(4, forms.size(), "the waived player has no team and is left out");

        PlayerForm rookie = forms.get("Big Rookie");
        assertEquals(3, rookie.gamesThisSeason());
        assertNull(rookie.trend(), "no last season, so no baseline");
        assertEquals(Form.EARLY_HOT, rookie.form());
        assertEquals(9000, rookie.crowd().count(), "only the latest sync counts");
        assertEquals(CrowdSignal.CONFIRMED_BREAKOUT, rookie.crowdSignal());
        assertEquals(CrowdSignal.EARLY_PICKUP, forms.get("Back From Injury").crowdSignal());
        assertEquals(Form.RETURNING, forms.get("Back From Injury").form());

        PlayerForm veteran = forms.get("Steady Veteran");
        assertEquals(Trend.Baseline.LAST_SEASON, veteran.trend().baselineSource());
        assertEquals(0, veteran.hotScore(), 1e-9);
        assertEquals(Form.NEUTRAL, veteran.form());
        assertEquals(CrowdSignal.NONE, veteran.crowdSignal(), "a neutral player being dropped is not panic");

        // the bench guard is outside the value league but still gets a value against it
        assertEquals(Form.NEUTRAL, forms.get("Bench Guard").form());
        assertEquals(true, forms.get("Bench Guard").value() < 0);
    }

    @Test
    void beforeTheFirstGameOfTheSeasonNobodyHasAValue() {
        players.saveAllAndFlush(List.of(player(1, "Star", "LAL", 8), player(2, "Rookie", "SAS", 0)));
        upsert.upsert(1, games(2026, 1, 10, 36, 30));                                  // last season only

        List<PlayerForm> forms = service().formOfAllPlayers(CATEGORIES);

        // no league to score against: no value, no trend, no crash
        assertEquals(2, forms.size());
        forms.forEach(form -> {
            assertNull(form.value(), form.player().name());
            assertNull(form.trend(), form.player().name());
            assertEquals(Form.NEUTRAL, form.form(), form.player().name());
        });
    }

    @Test
    void pointsLeagueScoresFantasyPointsAgainstItsOwnHighValue() {
        players.saveAllAndFlush(List.of(player(1, "Big Rookie", "DUK", 0), player(2, "Bench Guard", "BOS", 3)));
        upsert.upsert(1, games(2026, 10, 3, 34, 24));
        upsert.upsert(2, games(2026, 10, 3, 16, 4));

        Map<String, PlayerForm> forms = service().formOfAllPlayers(POINTS).stream()
                .collect(Collectors.toMap(form -> form.player().name(), form -> form));

        // two players in the league: one z-score each, +1 and -1
        assertEquals(1, forms.get("Big Rookie").value(), 1e-9);
        assertEquals(-1, forms.get("Bench Guard").value(), 1e-9);
        assertEquals(Form.NEUTRAL, forms.get("Big Rookie").form(), "+1 is below the points high value of 1.5");
        assertEquals(Form.EARLY_HOT, service().formOfAllPlayers(CATEGORIES).stream()
                .filter(form -> form.player().id() == 1).findFirst().orElseThrow().form(),
                "the same rookie is high value in 9-cat, against its threshold of 1");
    }

    // window 3, 5 baseline games, 15 minutes; hot at 0.15; high value 1 (9-cat) and 1.5 (points); league = 15+ minutes
    private TrendService service() {
        SeasonData seasonData = new SeasonData(players, gameLogs, snapshots, new TrendCalculator(3, 5, 15),
                new HotStreakScoring(0.4, 0.3, 0.3), CLOCK, 15);
        return new TrendService(seasonData, new FormClassifier(0.15, 18, 0.15), 0.5, 1, 1.5);
    }

    private static TrendingSnapshotEntity snapshot(long playerId, TrendingSnapshot.Type type, int count, String at) {
        return TrendingSnapshotEntity.from(new TrendingSnapshot(playerId, type, count, Instant.parse(at)));
    }

    private static PlayerEntity player(long id, String name, String team, int experienceYears) {
        return PlayerEntity.from(new Player(id, name, team, "G", null, experienceYears));
    }

    // {@code count} identical games on consecutive days from year-month-01
    private static List<GameLog> games(int year, int month, int count, int minutes, int points) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(day -> new GameLog(LocalDate.of(year, month, day), minutes,
                        points / 2, points, points / 10, points / 5, points / 4, minutes / 4, minutes / 6, 1, 1, 2, points))
                .toList();
    }
}
