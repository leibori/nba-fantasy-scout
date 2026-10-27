package com.ori_leibo.nbafantasyscout.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GameLogTest {

    @Test
    void seasonIsTheYearItEnds() {
        assertEquals(2027, game(LocalDate.of(2026, 10, 25)).season());
        assertEquals(2026, game(LocalDate.of(2026, 4, 12)).season());
        assertEquals(2026, game(LocalDate.of(2026, 7, 31)).season());
        assertEquals(2027, game(LocalDate.of(2026, 8, 1)).season());
    }

    @Test
    void seasonDatesMatchSeason() {
        assertEquals(LocalDate.of(2025, 8, 1), GameLog.seasonStart(2026));
        assertEquals(LocalDate.of(2026, 7, 31), GameLog.seasonEnd(2026));
        assertEquals(2026, game(GameLog.seasonStart(2026)).season());
        assertEquals(2026, game(GameLog.seasonEnd(2026)).season());
    }

    private static GameLog game(LocalDate date) {
        return new GameLog(date, 30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
