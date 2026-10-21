package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void breakoutComparesLastThreeGamesWithEarlierOnesRegardlessOfInputOrder() {
        List<GameLog> games = List.of(
                game(5, 30, 30), game(1, 20, 10), game(4, 30, 30), game(2, 20, 10), game(3, 30, 30));

        Trend trend = calculator.calculate(games).orElseThrow();

        assertEquals(30.0, trend.recent().points());
        assertEquals(10.0, trend.baseline().points());
        assertEquals(20.0, trend.delta(StatCategory.PTS));
    }

    @Test
    void benchedStarterIsKeptAsDecline() {
        List<GameLog> games = List.of(
                game(1, 36, 25), game(2, 36, 25), game(3, 12, 4), game(4, 12, 4), game(5, 12, 4));

        Trend trend = calculator.calculate(games).orElseThrow();

        assertEquals(-24.0, trend.minutesDelta());
        assertEquals(-21.0, trend.delta(StatCategory.PTS));
    }

    @Test
    void noiseIsFiltered() {
        List<GameLog> tooFewGames = List.of(game(1, 30, 20), game(2, 30, 20), game(3, 30, 20), game(4, 30, 20));
        List<GameLog> garbageTime = List.of(
                game(1, 5, 2), game(2, 5, 2), game(3, 8, 6), game(4, 8, 6), game(5, 8, 6));

        assertTrue(calculator.calculate(tooFewGames).isEmpty());
        assertTrue(calculator.calculate(garbageTime).isEmpty());
    }
}
