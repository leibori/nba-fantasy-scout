package com.ori_leibo.nbafantasyscout.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ori_leibo.nbafantasyscout.TestcontainersConfiguration;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import com.ori_leibo.nbafantasyscout.persistence.PlayerEntity;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import com.ori_leibo.nbafantasyscout.trend.HotStreakScoring;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import com.ori_leibo.nbafantasyscout.trend.TrendCalculator;
import java.time.LocalDate;
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

    private static final int SEASON = 2027;

    @Autowired
    private PlayerRepository players;
    @Autowired
    private GameLogRepository gameLogs;
    @Autowired
    private GameLogUpsert upsert;

    @Test
    void judgesRookiesAndVeteransEarlyInTheSeasonAndSkipsOffRosterPlayers() {
        players.saveAllAndFlush(List.of(
                player(1, "Big Rookie", "DUK"), player(2, "Steady Veteran", "LAL"),
                player(3, "Bench Guard", "BOS"), player(4, "Waived Player", null)));
        upsert.upsert(1, games(2026, 10, 3, 34, 24));                                  // rookie, 3 big games
        upsert.upsert(2, games(2026, 10, 3, 30, 15));                                  // veteran, as last season
        upsert.upsert(2, games(2026, 1, 10, 30, 15));
        upsert.upsert(3, games(2026, 10, 3, 8, 3));                                    // bench, 8 minutes
        upsert.upsert(3, games(2026, 1, 10, 8, 3));
        upsert.upsert(4, games(2026, 10, 3, 34, 30));

        // window 3, 5 baseline games, 15 minutes; hot at 0.15, high value from 1; league = 15+ minutes
        TrendService service = new TrendService(players, gameLogs, new TrendCalculator(3, 5, 15),
                new HotStreakScoring(0.4, 0.3, 0.3), new FormClassifier(new HotStreakScoring(0.4, 0.3, 0.3), 0.15, 1),
                SEASON, 15, 0.5);

        Map<String, PlayerForm> forms = service.formOfAllPlayers().stream()
                .collect(Collectors.toMap(form -> form.player().name(), form -> form));

        assertEquals(3, forms.size(), "the waived player has no team and is left out");

        PlayerForm rookie = forms.get("Big Rookie");
        assertEquals(3, rookie.gamesThisSeason());
        assertNull(rookie.trend(), "no last season, so no baseline");
        assertEquals(Form.EARLY_HOT, rookie.form());

        PlayerForm veteran = forms.get("Steady Veteran");
        assertEquals(Trend.Baseline.LAST_SEASON, veteran.trend().baselineSource());
        assertEquals(0, veteran.hotScore(), 1e-9);
        assertEquals(Form.NEUTRAL, veteran.form());

        // the bench guard is outside the value league but still gets a value against it
        assertEquals(Form.NEUTRAL, forms.get("Bench Guard").form());
        assertEquals(true, forms.get("Bench Guard").value() < 0);
    }

    private static PlayerEntity player(long id, String name, String team) {
        return PlayerEntity.from(new Player(id, name, team, "G", null));
    }

    // {@code count} identical games on consecutive days from year-month-01
    private static List<GameLog> games(int year, int month, int count, int minutes, int points) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(day -> new GameLog(LocalDate.of(year, month, day), minutes,
                        points / 2, points, points / 10, points / 5, points / 4, minutes / 4, minutes / 6, 1, 1, 2, points))
                .toList();
    }
}
