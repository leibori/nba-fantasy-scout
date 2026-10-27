package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrendCalculatorTest {

    // window 3, at least 2 baseline games, 15 minutes in the recent or baseline period
    private final TrendCalculator calculator = new TrendCalculator(3, 2, 15);

    private static GameLog game(int day, int minutes, int points) {
        return new GameLog(LocalDate.of(2026, 1, day), minutes, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, points);
    }

    private static GameLog lastSeasonGame(int day, int minutes, int points) {
        return new GameLog(LocalDate.of(2025, 3, day), minutes, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, points);
    }

    @Test
    void breakoutComparesLastThreeGamesWithEarlierOnesRegardlessOfInputOrder() {
        List<GameLog> games = List.of(
                game(5, 30, 30), game(1, 20, 10), game(4, 30, 30), game(2, 20, 10), game(3, 30, 30));

        Trend trend = calculator.calculate(games, List.of()).orElseThrow();

        assertEquals(30.0, trend.recent().points());
        assertEquals(10.0, trend.baseline().points());
        assertEquals(20.0, trend.delta(StatCategory.PTS));
        assertEquals(Trend.Baseline.THIS_SEASON, trend.baselineSource());
    }

    @Test
    void benchedStarterIsKeptAsDecline() {
        List<GameLog> games = List.of(
                game(1, 36, 25), game(2, 36, 25), game(3, 12, 4), game(4, 12, 4), game(5, 12, 4));

        Trend trend = calculator.calculate(games, List.of()).orElseThrow();

        assertEquals(-24.0, trend.minutesDelta());
        assertEquals(-21.0, trend.delta(StatCategory.PTS));
    }

    @Test
    void earlySeasonFallsBackToLastSeasonAsAProvisionalBaseline() {
        List<GameLog> thisSeason = List.of(game(1, 34, 28), game(2, 34, 28), game(3, 34, 28), game(4, 34, 28));
        List<GameLog> lastSeason = List.of(lastSeasonGame(1, 20, 9), lastSeasonGame(2, 20, 11));

        Trend trend = calculator.calculate(thisSeason, lastSeason).orElseThrow();

        // 4 games: 3 recent + only 1 earlier this season, so last season is the baseline
        assertEquals(28.0, trend.recent().points());
        assertEquals(10.0, trend.baseline().points());
        assertEquals(Trend.Baseline.LAST_SEASON, trend.baselineSource());
        assertTrue(trend.provisional());
    }

    @Test
    void prefersThisSeasonOnceItHasEnoughEarlierGames() {
        List<GameLog> thisSeason = List.of(
                game(1, 30, 15), game(2, 30, 15), game(3, 30, 20), game(4, 30, 20), game(5, 30, 20));
        List<GameLog> lastSeason = List.of(lastSeasonGame(1, 20, 5), lastSeasonGame(2, 20, 5));

        Trend trend = calculator.calculate(thisSeason, lastSeason).orElseThrow();

        assertEquals(15.0, trend.baseline().points());
        assertFalse(trend.provisional());
    }

    @Test
    void noiseIsFiltered() {
        List<GameLog> rookie = List.of(game(1, 30, 20), game(2, 30, 20), game(3, 30, 20), game(4, 30, 20));
        List<GameLog> twoGames = List.of(game(1, 30, 20), game(2, 30, 20));
        List<GameLog> lastSeason = List.of(lastSeasonGame(1, 30, 20), lastSeasonGame(2, 30, 20));
        List<GameLog> garbageTime = List.of(
                game(1, 5, 2), game(2, 5, 2), game(3, 8, 6), game(4, 8, 6), game(5, 8, 6));

        assertTrue(calculator.calculate(rookie, List.of()).isEmpty(), "no baseline anywhere");
        assertTrue(calculator.calculate(twoGames, lastSeason).isEmpty(), "too few games for the recent window");
        assertTrue(calculator.calculate(garbageTime, List.of()).isEmpty(), "low minutes in both periods");
    }
}
